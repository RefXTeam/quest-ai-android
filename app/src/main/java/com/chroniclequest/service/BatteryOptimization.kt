package com.chroniclequest.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Helpers to keep the ambient service alive in the background. Aggressive OEM
 * battery managers (Samsung, etc.) will kill even a foreground service unless the
 * app is exempt from battery optimization — so we ask the user to allow it.
 */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the system dialog asking the user to exempt this app. No-op if already exempt. */
    @SuppressLint("BatteryLife")
    fun requestIgnore(context: Context) {
        if (isIgnoring(context)) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        runCatching { context.startActivity(intent) }
    }
}
