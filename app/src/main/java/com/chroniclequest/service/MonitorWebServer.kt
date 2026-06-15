package com.chroniclequest.service

import android.content.Context
import android.util.Log
import com.chroniclequest.domain.PipelineMonitor
import com.chroniclequest.domain.model.PipelineEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny embedded HTTP server for the live demo monitor. Serves the static page
 * (assets/monitor.html) and a JSON feed of [PipelineMonitor] events. A laptop on
 * the same Wi-Fi opens http://<phone-ip>:8080 to watch the pipeline in real time.
 * CORS is open so the GitHub-Pages copy can also poll (HTTP origins only).
 */
@Singleton
class MonitorWebServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monitor: PipelineMonitor,
    private val json: Json,
) {
    private var server: Server? = null

    val port: Int get() = PORT
    val isRunning: Boolean get() = server?.isAlive == true

    fun start() {
        if (server != null) return
        runCatching {
            server = Server().also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            Log.d(TAG, "Monitor web server started on :$PORT")
        }.onFailure { Log.e(TAG, "Failed to start web server", it) }
    }

    fun stop() {
        server?.stop()
        server = null
        Log.d(TAG, "Monitor web server stopped")
    }

    private inner class Server : NanoHTTPD(PORT) {
        override fun serve(session: IHTTPSession): Response {
            val response = when (session.uri) {
                "/", "/index.html" -> asset("monitor.html", "text/html")
                "/events.json" -> newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    json.encodeToString(ListSerializer(PipelineEvent.serializer()), monitor.events.value),
                )
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Cache-Control", "no-store")
            return response
        }

        private fun asset(name: String, mime: String): Response {
            val bytes = context.assets.open(name).use { it.readBytes() }
            return newFixedLengthResponse(
                Response.Status.OK,
                mime,
                ByteArrayInputStream(bytes),
                bytes.size.toLong(),
            )
        }
    }

    private companion object {
        const val TAG = "MonitorWebServer"
        const val PORT = 8080
    }
}
