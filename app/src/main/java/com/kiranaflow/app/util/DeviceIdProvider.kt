package com.kiranaflow.app.util

import android.content.Context
import android.provider.Settings

/**
 * Provides a unique device identifier for the KiranaFlow app
 * 
 * Uses Android ID as the device identifier, which is unique per device
 * and persists across app reinstalls (but not factory resets).
 */
object DeviceIdProvider {
    
    private var cachedDeviceId: String? = null
    
    /**
     * Get the unique device ID
     * 
     * @param context Application context
     * @return Unique device identifier
     */
    fun getDeviceId(context: Context): String {
        return cachedDeviceId ?: run {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            
            // Android ID can be null in some rare cases, fallback to random UUID
            val deviceId = androidId ?: java.util.UUID.randomUUID().toString()
            
            cachedDeviceId = deviceId
            deviceId
        }
    }
    
    /**
     * Get a human-readable device name for display purposes
     * 
     * @param context Application context
     * @return Device name (manufacturer + model)
     */
    fun getDeviceName(context: Context): String {
        val manufacturer = android.os.Build.MANUFACTURER
        val model = android.os.Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model
        } else {
            "$manufacturer $model"
        }
    }
}
