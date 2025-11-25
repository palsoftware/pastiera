package it.palsoftware.pastiera.inputmethod

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.update.GITHUB_LATEST_RELEASE_PAGE

/**
 * Helper for managing app notifications.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "pastiera_nav_mode_channel"
    private const val NOTIFICATION_ID = 1
    
    private const val UPDATE_CHANNEL_ID = "pastiera_update_channel"
    private const val UPDATE_NOTIFICATION_ID = 2
    
    /**
     * Checks whether notification permission is granted.
     * On Android 13+ (API 33+) POST_NOTIFICATIONS is required.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires POST_NOTIFICATIONS permission
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 and below do not require explicit notification permissions
            true
        }
    }
    
    /**
     * Triggers a haptic feedback vibration.
     * @param context The context to get the vibrator service
     * @param durationMs Duration of the vibration in milliseconds (default: 30ms)
     */
    fun triggerHapticFeedback(context: Context, durationMs: Long = 30) {
        if (tryModernHapticFeedback(context)) {
            return
        }
        
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator == null || !vibrator.hasVibrator()) {
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            android.util.Log.w("NotificationHelper", "Unable to trigger haptic feedback", e)
        }
    }

    /**
     * Triggers a short vibration for nav mode activation, without showing a notification.
     */
    fun vibrateNavModeActivated(context: Context) {
        triggerHapticFeedback(context, 50)
    }
    
    private fun tryModernHapticFeedback(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return false
        }
        
        return try {
            val contextClass = Context::class.java
            val hapticManagerClass = Class.forName("android.os.HapticFeedbackManager")
            val getSystemServiceMethod = contextClass.getMethod("getSystemService", Class::class.java)
            val hapticManager = getSystemServiceMethod.invoke(context, hapticManagerClass) ?: return false
            
            val performMethod = hapticManagerClass.getMethod(
                "performHapticFeedback",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val hapticAutocorrect = hapticManagerClass.getField("HAPTIC_AUTOCORRECT").getInt(null)
            val ignoreViewSetting = hapticManagerClass.getField("FLAG_IGNORE_VIEW_SETTING").getInt(null)
            val successCode = hapticManagerClass.getField("HAPTIC_FEEDBACK_SUCCESS").getInt(null)
            
            val result = performMethod.invoke(
                hapticManager,
                hapticAutocorrect,
                ignoreViewSetting
            ) as? Int ?: return false
            
            result == successCode
        } catch (e: Exception) {
            android.util.Log.d("NotificationHelper", "Modern haptic feedback unavailable", e)
            false
        }
    }

    /**
     * Creates the notification channel (required on Android 8.0+).
     * Uses IMPORTANCE_DEFAULT for normal priority notification.
     * Deletes and recreates the channel if it already exists to apply new settings.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Delete existing channel if it exists to recreate with new settings
            try {
                notificationManager.deleteNotificationChannel(CHANNEL_ID)
            } catch (e: Exception) {
                // Channel doesn't exist, that's fine
            }
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_nav_mode_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT // Normal priority notification
            ).apply {
                description = context.getString(R.string.notification_nav_mode_channel_description)
                setShowBadge(false)
                enableLights(false) // Disable LED light
                enableVibration(true) // Enable vibration
                // Set vibration pattern: short vibration (50ms)
                vibrationPattern = longArrayOf(0, 50)
                setSound(null, null) // No sound
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Creates the notification channel for update notifications (Android 8.0+).
     */
    private fun createUpdateNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val channel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                context.getString(R.string.notification_update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_update_channel_description)
                setShowBadge(true)
                enableLights(false)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 50)
                setSound(null, null)
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Creates a bitmap icon with the letter "N" for the nav mode notification.
     * @param size Icon size in pixels
     * @param backgroundColor Background color (default transparent)
     * @param textColor Text color (default white)
     */
    private fun createNavModeIcon(
        size: Int,
        backgroundColor: Int = Color.TRANSPARENT,
        textColor: Int = Color.WHITE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw background
        if (backgroundColor != Color.TRANSPARENT) {
            canvas.drawColor(backgroundColor)
        } else {
            canvas.drawColor(Color.TRANSPARENT)
        }
        
        // Draw the "N" letter
        val paint = Paint().apply {
            color = textColor
            textSize = size * 0.7f // 70% of size to keep margins
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        // Compute vertical position to center the text
        val textY = (canvas.height / 2) - ((paint.descent() + paint.ascent()) / 2)
        
        // Draw "N"
        canvas.drawText("N", canvas.width / 2f, textY, paint)
        
        return bitmap
    }
    
    /**
     * Shows a notification when a new app update is available.
     * Respects notification permissions on Android 13+.
     */
    fun showUpdateAvailableNotification(
        context: Context,
        latestVersion: String,
        downloadUrl: String?
    ) {
        if (!hasNotificationPermission(context)) {
            android.util.Log.w("NotificationHelper", "Notification permission not granted")
            return
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createUpdateNotificationChannel(context)
        }
        
        // Open the direct APK download if available, otherwise the GitHub releases page.
        val targetUrl = downloadUrl ?: GITHUB_LATEST_RELEASE_PAGE
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntentFlags = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else -> PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            pendingIntentFlags
        )
        
        val notificationBuilder = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_update_available_title))
            .setContentText(
                context.getString(
                    R.string.notification_update_available_text,
                    latestVersion
                )
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            notificationBuilder.setVibrate(longArrayOf(0, 50))
        }
        
        val notification = notificationBuilder.build()
        notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
    }
    
    /**
     * Cancels the nav mode notification.
     */
    fun cancelNavModeNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
