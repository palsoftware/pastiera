package it.palsoftware.pastiera.inputmethod.whisper

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import java.io.File

/**
 * Manages ONNX Runtime sessions for Whisper speech recognition.
 * Loads and manages the 5 separate ONNX models (optimized from RTranslator).
 * 
 * Models:
 * 1. initSession - Audio PCM → Mel-Spectrogram
 * 2. encoderSession - Mel-Spectrogram → Encoder Hidden States
 * 3. cacheInitSession - Initialize KV-Cache
 * 4. decoderSession - Iterative token generation with KV-Cache
 * 5. detokenizerSession - Token sequence → Text
 */
class WhisperOnnxManager(private val context: Context) {
    
    private var initSession: OrtSession? = null
    private var encoderSession: OrtSession? = null
    private var cacheInitSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var detokenizerSession: OrtSession? = null
    private var onnxEnv: OrtEnvironment? = null
    
    private val TAG = "WhisperOnnxManager"
    // Directory where downloaded models are stored (by WhisperModelDownloader)
    private val MODELS_DIR = File(context.getExternalFilesDir(null), "whisper_models")
    
    /**
     * Initializes and loads all 5 ONNX sessions.
     * Models must be pre-downloaded via WhisperModelDownloader.
     */
    fun initialize(): Result<Unit> {
        return try {
            Log.d(TAG, "Initializing ONNX Environment...")
            onnxEnv = OrtEnvironment.getEnvironment()
            
            // Check if models are available
            if (!isModelsAvailable()) {
                return Result.failure(Exception("Whisper models not found. Please download them first."))
            }
            
            Log.d(TAG, "Loading ONNX sessions...")
            loadSessions()
            
            Log.d(TAG, "✅ All ONNX sessions initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ONNX Manager", e)
            Result.failure(e)
        }
    }
    
    /**
     * Loads all 5 ONNX sessions from downloaded models.
     */
    private fun loadSessions() {
        val env = onnxEnv ?: throw IllegalStateException("ONNX Environment not initialized")
        
        try {
            // Find the main model file (the ZIP that was extracted)
            val modelFiles = MODELS_DIR.listFiles() ?: emptyArray()
            val onnxFiles = modelFiles.filter { it.extension == "onnx" }
            
            if (onnxFiles.isEmpty()) {
                throw IllegalStateException("No ONNX files found in ${MODELS_DIR.absolutePath}")
            }
            
            // Extract the base path from the first ONNX file (they should all be in same dir)
            val baseDir = onnxFiles[0].parentFile?.absolutePath ?: MODELS_DIR.absolutePath
            
            Log.d(TAG, "Found ONNX models in: $baseDir")
            onnxFiles.forEach { Log.d(TAG, "  - ${it.name} (${it.length() / (1024 * 1024)} MB)") }
            
            // Load the 5 sessions
            // 1. Initializer (Mel-Spectrogram generation)
            val initPath = findModelFile(onnxFiles, "initializer", "init")
            if (initPath != null) {
                Log.d(TAG, "Loading initializer session from $initPath")
                initSession = createSession(env, initPath, "init")
            }
            
            // 2. Encoder
            val encoderPath = findModelFile(onnxFiles, "encoder")
            if (encoderPath != null) {
                Log.d(TAG, "Loading encoder session from $encoderPath")
                encoderSession = createSession(env, encoderPath, "encoder")
            }
            
            // 3. Cache Initializer
            val cacheInitPath = findModelFile(onnxFiles, "cache_initializer", "cache_init")
            if (cacheInitPath != null) {
                Log.d(TAG, "Loading cache init session from $cacheInitPath")
                cacheInitSession = createSession(env, cacheInitPath, "cache_init")
            }
            
            // 4. Decoder
            val decoderPath = findModelFile(onnxFiles, "decoder")
            if (decoderPath != null) {
                Log.d(TAG, "Loading decoder session from $decoderPath")
                decoderSession = createSession(env, decoderPath, "decoder")
            }
            
            // 5. Detokenizer
            val detokenizerPath = findModelFile(onnxFiles, "detokenizer")
            if (detokenizerPath != null) {
                Log.d(TAG, "Loading detokenizer session from $detokenizerPath")
                detokenizerSession = createSession(env, detokenizerPath, "detokenizer")
            }
            
            // Verify all sessions are loaded
            if (initSession == null || encoderSession == null || cacheInitSession == null || 
                decoderSession == null || detokenizerSession == null) {
                throw IllegalStateException("Failed to load one or more ONNX models")
            }
            
            Log.d(TAG, "✅ All 5 ONNX sessions loaded successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sessions: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Finds an ONNX model file by pattern matching.
     */
    private fun findModelFile(files: List<File>, vararg patterns: String): String? {
        for (file in files) {
            for (pattern in patterns) {
                if (file.name.lowercase().contains(pattern.lowercase())) {
                    return file.absolutePath
                }
            }
        }
        return null
    }
    
    /**
     * Creates an ONNX session with optimized settings.
     * Based on RTranslator's session configuration.
     */
    private fun createSession(env: OrtEnvironment, modelPath: String, sessionType: String): OrtSession {
        val sessionOptions = OrtSession.SessionOptions().apply {
            
            when (sessionType) {
                "encoder" -> {
                    // Encoder uses CPU arena allocation for better performance
                    val totalMemory = getTotalRamSize()
                    if (totalMemory <= 7000) {
                        setCPUArenaAllocator(false)
                        setMemoryPatternOptimization(false)
                    } else {
                        setCPUArenaAllocator(true)
                        setMemoryPatternOptimization(true)
                    }
                    setSymbolicDimensionValue("batch_size", 1)
                }
                
                "cache_init", "decoder", "detokenizer" -> {
                    // Cache and decoder sessions: register custom op library via reflection (required for KV-Cache)
                    try {
                        val ortxPackageClass = Class.forName("ai.onnxruntime.extensions.OrtxPackage")
                        val getLibraryPathMethod = ortxPackageClass.getMethod("getLibraryPath")
                        val libraryPath = getLibraryPathMethod.invoke(null) as String
                        registerCustomOpLibrary(libraryPath)
                        Log.d(TAG, "✅ Registered OrtxPackage custom op library for $sessionType")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Could not register OrtxPackage: ${e.message}")
                        Log.w(TAG, "Decoder will work without KV-Cache custom ops - may be slower")
                    }
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
            }
            
            // Disable optimization for all sessions to match RTranslator
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        }
        
        try {
            val session = env.createSession(modelPath, sessionOptions)
            Log.d(TAG, "✅ Created $sessionType session")
            return session
        } catch (e: OrtException) {
            Log.e(TAG, "Failed to create $sessionType session: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Gets total device RAM in MB.
     */
    private fun getTotalRamSize(): Long {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / 1000000L
    }
    
    /**
     * Checks if downloaded models are available.
     */
    private fun isModelsAvailable(): Boolean {
        if (!MODELS_DIR.exists()) {
            Log.e(TAG, "Models directory doesn't exist: ${MODELS_DIR.absolutePath}")
            return false
        }
        
        val modelFiles = MODELS_DIR.listFiles() ?: emptyArray()
        val hasOnnxFiles = modelFiles.any { it.extension == "onnx" }
        
        if (!hasOnnxFiles) {
            Log.e(TAG, "No ONNX files found in models directory")
            return false
        }
        
        Log.d(TAG, "Found ${modelFiles.count { it.extension == "onnx" }} ONNX model files")
        return true
    }
    
    // ===== Getters =====
    fun getInitSession() = initSession
    fun getEncoderSession() = encoderSession
    fun getCacheInitSession() = cacheInitSession
    fun getDecoderSession() = decoderSession
    fun getDetokenizerSession() = detokenizerSession
    fun getOnnxEnv() = onnxEnv
    
    fun isInitialized(): Boolean {
        return initSession != null && encoderSession != null && 
               cacheInitSession != null && decoderSession != null && 
               detokenizerSession != null && onnxEnv != null
    }
    
    fun destroy() {
        Log.d(TAG, "Destroying ONNX Manager...")
        try {
            initSession?.close()
            encoderSession?.close()
            cacheInitSession?.close()
            decoderSession?.close()
            detokenizerSession?.close()
            onnxEnv?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying sessions: ${e.message}")
        }
    }
    
    companion object {
        @Volatile
        private var instance: WhisperOnnxManager? = null
        
        fun getInstance(context: Context): WhisperOnnxManager {
            return instance ?: synchronized(this) {
                instance ?: WhisperOnnxManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
        
        fun destroy() {
            instance?.destroy()
            instance = null
        }
    }
}
