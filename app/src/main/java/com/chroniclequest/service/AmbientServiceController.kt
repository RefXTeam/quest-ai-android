package com.chroniclequest.service

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper so ViewModels can start/stop the foreground service without holding an Activity. */
@Singleton
class AmbientServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start() {
        ContextCompat.startForegroundService(context, AmbientAudioService.startIntent(context))
    }

    fun stop() {
        context.startService(AmbientAudioService.stopIntent(context))
    }
}
