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
 * Verifies a "walk N steps" quest using [Sensor.TYPE_STEP_COUNTER]. The counter is
 * a monotonic since-boot value, so each quest records a baseline at arm time and
 * completes when (current − baseline) ≥ its target. Requires ACTIVITY_RECOGNITION.
 */
@Singleton
class StepCountVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : QuestVerifier, SensorEventListener {

    override val method = VerificationMethod.STEP_COUNT

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val baselines = ConcurrentHashMap<Long, Float>()
    private val targets = ConcurrentHashMap<Long, Quest>()
    private val callbacks = ConcurrentHashMap<Long, (Long) -> Unit>()
    private var latestCount: Float = Float.NaN
    private var listening = false

    override fun start(quest: Quest, onComplete: (Long) -> Unit) {
        if (stepSensor == null) {
            Log.w(TAG, "No step counter on device — quest #${quest.id} cannot auto-verify")
            return
        }
        targets[quest.id] = quest
        callbacks[quest.id] = onComplete
        // Baseline is set on the first sensor event after arming.
        baselines[quest.id] = if (latestCount.isNaN()) Float.NaN else latestCount
        ensureListening()
        Log.d(TAG, "Watching step quest #${quest.id} (+${quest.targetValue} steps)")
    }

    override fun stop(questId: Long) {
        targets.remove(questId)
        callbacks.remove(questId)
        baselines.remove(questId)
        if (targets.isEmpty()) teardownListening()
    }

    override fun progress(questId: Long, now: Long): QuestProgress? {
        val quest = targets[questId] ?: return null
        val baseline = baselines[questId]
        val walked = if (baseline == null || baseline.isNaN() || latestCount.isNaN()) {
            0
        } else {
            (latestCount - baseline).toInt().coerceAtLeast(0)
        }
        return QuestProgress(current = walked, target = quest.targetValue)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values.firstOrNull() ?: return
        latestCount = total

        targets.values.forEach { quest ->
            val baseline = baselines[quest.id]
            if (baseline == null || baseline.isNaN()) {
                baselines[quest.id] = total // first reading after arming
                return@forEach
            }
            if (total - baseline >= quest.targetValue) {
                callbacks[quest.id]?.invoke(quest.id)
                stop(quest.id)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun ensureListening() {
        if (listening || stepSensor == null) return
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
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
