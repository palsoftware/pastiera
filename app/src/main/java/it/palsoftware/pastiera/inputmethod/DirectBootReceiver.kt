package it.palsoftware.pastiera.inputmethod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver to handle Direct Boot events.
 * 
 * Direct Boot allows the keyboard to function before the device is unlocked,
 * which is essential for entering PIN/password or using the keyboard in the lock screen.
 * 
 * This receiver listens for:
 * - LOCKED_BOOT_COMPLETED: Device has booted, but user hasn't unlocked it yet
 * - BOOT_COMPLETED: Device has booted and user has unlocked it
 */
class DirectBootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "DirectBootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.i(TAG, "Device booted in Direct Boot mode (locked)")
                // Initialize device-protected storage
                initializeDeviceProtectedStorage(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "Device fully booted and unlocked")
                // Migrate settings from credential-protected to device-protected storage
                migrateToDeviceProtectedStorage(context)
            }
        }
    }
    
    /**
     * Initialize services using device-protected storage.
     * This storage is available before the device is unlocked.
     */
    private fun initializeDeviceProtectedStorage(context: Context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val deviceContext = context.createDeviceProtectedStorageContext()
                // Access preferences to ensure they're created
                val prefs = deviceContext.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
                Log.i(TAG, "Device-protected storage initialized with ${prefs.all.size} settings")
                
                // Note: Android may still use system keyboard during locked boot for security.
                // This is a system limitation - third-party IMEs may be restricted during
                // credential entry to prevent potential keylogging or security exploits.
                Log.i(TAG, "IME service is directBootAware and ready, but Android may use system keyboard for security")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing device-protected storage", e)
        }
    }
    
    /**
     * Migrate settings from credential-protected to device-protected storage.
     * This ensures settings persist across reboots even before unlock.
     */
    private fun migrateToDeviceProtectedStorage(context: Context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val credentialContext = context
                val deviceContext = context.createDeviceProtectedStorageContext()
                
                val credentialPrefs = credentialContext.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
                val devicePrefs = deviceContext.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
                
                // If device-protected storage is empty but credential storage has data, migrate
                if (devicePrefs.all.isEmpty() && credentialPrefs.all.isNotEmpty()) {
                    Log.i(TAG, "Migrating ${credentialPrefs.all.size} settings to device-protected storage")
                    
                    val editor = devicePrefs.edit()
                    credentialPrefs.all.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is String -> editor.putString(key, value)
                            is Set<*> -> @Suppress("UNCHECKED_CAST")
                                editor.putStringSet(key, value as Set<String>)
                        }
                    }
                    editor.apply()
                    Log.i(TAG, "Settings migration completed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during storage migration", e)
        }
    }
}
