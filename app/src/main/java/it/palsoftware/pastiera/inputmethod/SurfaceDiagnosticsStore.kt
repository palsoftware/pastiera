package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.util.AtomicFile
import it.palsoftware.pastiera.BuildConfig
import java.io.File
import java.util.concurrent.Executors

internal data class SurfaceDiagnosticEvent(
    val timestampMs: Long,
    val sequence: Long,
    val event: String,
    val mode: String,
    val renderedSurface: String,
    val inputViewShown: Boolean,
    val inputViewActive: Boolean,
    val hasInputConnection: Boolean,
    val navModeLatched: Boolean,
    val details: String
)

internal class SurfaceDiagnosticRingBuffer(
    private val maximumSize: Int
) {
    private val events = ArrayDeque<SurfaceDiagnosticEvent>()

    init {
        require(maximumSize > 0)
    }

    fun add(event: SurfaceDiagnosticEvent) {
        events.addLast(event)
        while (events.size > maximumSize) {
            events.removeFirst()
        }
    }

    fun replaceWith(restoredEvents: Iterable<SurfaceDiagnosticEvent>) {
        events.clear()
        restoredEvents.forEach(::add)
    }

    fun clear() = events.clear()

    fun snapshot(): List<SurfaceDiagnosticEvent> = events.toList()
}

internal object SurfaceDiagnosticCodec {
    private const val FIELD_SEPARATOR = '\t'
    private const val FIELD_COUNT = 10

    fun encode(event: SurfaceDiagnosticEvent): String = listOf(
        event.timestampMs.toString(),
        event.sequence.toString(),
        sanitize(event.event),
        sanitize(event.mode),
        sanitize(event.renderedSurface),
        event.inputViewShown.toString(),
        event.inputViewActive.toString(),
        event.hasInputConnection.toString(),
        event.navModeLatched.toString(),
        sanitize(event.details)
    ).joinToString(FIELD_SEPARATOR.toString())

    fun decode(line: String): SurfaceDiagnosticEvent? {
        val fields = line.split(FIELD_SEPARATOR, limit = FIELD_COUNT)
        if (fields.size != FIELD_COUNT) return null
        return SurfaceDiagnosticEvent(
            timestampMs = fields[0].toLongOrNull() ?: return null,
            sequence = fields[1].toLongOrNull() ?: return null,
            event = fields[2],
            mode = fields[3],
            renderedSurface = fields[4],
            inputViewShown = parseBoolean(fields[5]) ?: return null,
            inputViewActive = parseBoolean(fields[6]) ?: return null,
            hasInputConnection = parseBoolean(fields[7]) ?: return null,
            navModeLatched = parseBoolean(fields[8]) ?: return null,
            details = fields[9]
        )
    }

    private fun parseBoolean(value: String): Boolean? = when (value) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun sanitize(value: String): String = value
        .replace('\t', ' ')
        .replace('\n', ' ')
        .replace('\r', ' ')
}

internal object SurfaceDiagnosticsStore {
    private const val MAX_EVENTS = 500
    private const val DIAGNOSTICS_DIRECTORY = "surface-diagnostics"
    private const val DIAGNOSTICS_FILE = "ime-surface-events.tsv"

    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pastiera-surface-diagnostics").apply { isDaemon = true }
    }
    private val buffer = SurfaceDiagnosticRingBuffer(MAX_EVENTS)
    private var initializedFile: File? = null
    private var dirtyGeneration = 0L
    private var writeScheduled = false

    val enabled: Boolean
        get() = BuildConfig.ENABLE_SURFACE_DIAGNOSTICS

    fun record(context: Context, event: SurfaceDiagnosticEvent) {
        if (!enabled) return
        synchronized(lock) {
            val file = ensureInitialized(context.applicationContext)
            buffer.add(event)
            dirtyGeneration += 1
            scheduleWriteLocked(file)
        }
    }

    fun snapshot(context: Context): List<SurfaceDiagnosticEvent> = synchronized(lock) {
        ensureInitialized(context.applicationContext)
        buffer.snapshot()
    }

    fun clear(context: Context) {
        if (!enabled) return
        synchronized(lock) {
            val file = ensureInitialized(context.applicationContext)
            buffer.clear()
            dirtyGeneration += 1
            scheduleWriteLocked(file)
        }
    }

    private fun ensureInitialized(context: Context): File {
        val file = File(File(context.filesDir, DIAGNOSTICS_DIRECTORY), DIAGNOSTICS_FILE)
        if (initializedFile?.absolutePath == file.absolutePath) return file

        file.parentFile?.mkdirs()
        val restoredEvents = runCatching {
            if (file.isFile) {
                file.useLines { lines -> lines.mapNotNull(SurfaceDiagnosticCodec::decode).toList() }
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
        buffer.replaceWith(restoredEvents)
        initializedFile = file
        dirtyGeneration = 0L
        writeScheduled = false
        return file
    }

    private fun scheduleWriteLocked(file: File) {
        if (writeScheduled) return
        writeScheduled = true
        writer.execute { persistUntilCurrent(file) }
    }

    private fun persistUntilCurrent(file: File) {
        while (true) {
            val generation: Long
            val snapshot: List<SurfaceDiagnosticEvent>
            synchronized(lock) {
                generation = dirtyGeneration
                snapshot = buffer.snapshot()
            }

            runCatching { writeAtomically(file, snapshot) }

            synchronized(lock) {
                if (generation == dirtyGeneration) {
                    writeScheduled = false
                    return
                }
            }
        }
    }

    private fun writeAtomically(file: File, events: List<SurfaceDiagnosticEvent>) {
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            val streamWriter = output.bufferedWriter(Charsets.UTF_8)
            events.forEach { event ->
                streamWriter.appendLine(SurfaceDiagnosticCodec.encode(event))
            }
            streamWriter.flush()
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw exception
        }
    }
}
