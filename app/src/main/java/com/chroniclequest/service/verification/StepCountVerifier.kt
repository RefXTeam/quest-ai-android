package com.chroniclequest.service.verification

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.chroniclequest.domain.model.Quest
import com.chroniclequest.domain.model.QuestProgress
import com.chroniclequest.domain.model.VerificationMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies a "walk N steps" quest. Prefers [Sensor.TYPE_STEP_DETECTOR] (one event
 * per step, no batching → real-time progress) and counts steps since arming. Falls
 * back to [Sensor.TYPE_STEP_COUNTER] (monotonic total, baseline delta) on devices
 * without a detector. **Requires the ACTIVITY_RECOGNITION runtime permission** —
 * without it the OS delivers no step events at all.
 */
@Singleton
class StepCountVerifier @Inject constructor(
    @ApplicationContext context: Context,
) : QuestVerifier, SensorEventListener {

    override val method = VerificationMethod.STEP_COUNT

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepCounter: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val useDetector = stepDetector != null
    private val sensor: Sensor? = stepDetector ?: stepCounter

    private val targets = ConcurrentHashMap<Long, Quest>()
    private val callbacks = ConcurrentHashMap<Long, (Long) -> Unit>()
    // Detector mode: steps counted since arming. Counter mode: baseline total at arm.
    private val walked = ConcurrentHashMap<Long, Int>()
    private val baselines = ConcurrentHashMap<Long, Float>()
    private var latestCount: Float = Float.NaN
    private var listening = false

    override fun start(quest: Quest, onComplete: (Long) -> Unit) {
        if (sensor == null) {
            Log.w(TAG, "No step sensor — quest #${quest.id} cannot auto-verify")
            return
        }
        targets[quest.id] = quest
        callbacks[quest.id] = onComplete
        if (useDetector) {
            walked[quest.id] = 0
        } else {
            baselines[quest.id] = if (latestCount.isNaN()) Float.NaN else latestCount
        }
        ensureListening()
        Log.d(TAG, "Watching step quest #${quest.id} (+${quest.targetValue}, detector=$useDetector)")
    }

    override fun stop(questId: Long) {
        targets.remove(questId)
        callbacks.remove(questId)
        walked.remove(questId)
        baselines.remove(questId)
        if (targets.isEmpty()) teardownListening()
    }

    override fun progress(questId: Long, now: Long): QuestProgress? {
        val quest = targets[questId] ?: return null
        val steps = if (useDetector) {
            walked[questId] ?: 0
        } else {
            val baseline = baselines[questId]
            if (baseline == null || baseline.isNaN() || latestCount.isNaN()) 0
            else (latestCount - baseline).toInt().coerceAtLeast(0)
        }
        return QuestProgress(current = steps, target = quest.targetValue)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                val inc = event.values.firstOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
                Log.d(TAG, "step detector +$inc")
                targets.keys.toList().forEach { id ->
                    val total = (walked[id] ?: 0) + inc
                    walked[id] = total
                    val target = targets[id]?.targetValue ?: return@forEach
                    if (total >= target) {
                        callbacks[id]?.invoke(id)
                        stop(id)
                    }
                }
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val total = event.values.firstOrNull() ?: return
                latestCount = total
                Log.d(TAG, "step counter total=$total")
                targets.values.forEach { quest ->
                    val baseline = baselines[quest.id]
                    if (baseline == null || baseline.isNaN()) {
                        baselines[quest.id] = total
                        return@forEach
                    }
                    if (total - baseline >= quest.targetValue) {
                        callbacks[quest.id]?.invoke(quest.id)
                        stop(quest.id)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun ensureListening() {
        if (listening || sensor == null) return
        // FASTEST + no batching for immediate per-step updates during the demo.
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, 0)
        listening = true
    }

    private fun teardownListening() {
        if (!listening) return
        sensorManager.unregisterListener(this)
        listening = false
        latestCount = Float.NaN
    }

    companion object {
        private const val TAG = "StepCountVerifier"
    }
}
