package com.nishant.crywise

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioRecord
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Listens through the microphone and runs Google's YAMNet sound model on the phone.
 * YAMNet recognises 521 everyday sounds, including "Baby cry, infant cry".
 * Nothing is recorded or uploaded; audio is analysed in memory and thrown away.
 */
class CryMonitor(private val context: Context) {

    var listening by mutableStateOf(false); private set
    var crying by mutableStateOf(false); private set
    var cryScore by mutableFloatStateOf(0f); private set
    var level by mutableFloatStateOf(0f); private set
    var crySeconds by mutableIntStateOf(0); private set
    var intensity by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    /** Called on the main thread when a cry starts, and when it ends (with duration). */
    var onCryStart: (() -> Unit)? = null
    var onCryEnd: ((start: Long, seconds: Int, intensity: String?) -> Unit)? = null

    private var classifier: AudioClassifier? = null
    private var record: AudioRecord? = null
    private var job: Job? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (listening) return
        error = null
        try {
            val c = classifier ?: AudioClassifier.createFromFile(context, "yamnet.tflite").also { classifier = it }
            val tensor = c.createInputTensorAudio()
            val rec = c.createAudioRecord()
            record = rec
            rec.startRecording()
            listening = true

            job = scope.launch(Dispatchers.Default) {
                val window = ArrayDeque<Boolean>()   // last ~5 seconds, 2 checks per second
                var isCrying = false
                var cryStart = 0L
                var levelSum = 0f
                var levelCount = 0
                var screamPeak = 0f
                var quietBefore = 0f

                while (isActive) {
                    tensor.load(rec)
                    val cats = c.classify(tensor).firstOrNull()?.categories.orEmpty()
                    fun score(label: String) = cats.firstOrNull { it.label == label }?.score ?: 0f
                    val baby = maxOf(score("Baby cry, infant cry"), score("Crying, sobbing"))
                    val scream = maxOf(score("Screaming"), score("Wail, moan"))

                    val samples = tensor.tensorBuffer.floatArray
                    var sum = 0.0
                    for (x in samples) sum += x * x
                    val rms = sqrt(sum / samples.size.coerceAtLeast(1))
                    val db = 20 * log10(rms + 1e-9)
                    val lvl = ((db + 60) / 60).toFloat().coerceIn(0f, 1f)

                    window.addLast(baby > 0.25f)
                    if (window.size > 10) window.removeFirst()
                    val hits = window.count { it }

                    var started = false
                    var ended: Triple<Long, Int, String?>? = null

                    if (!isCrying && hits >= 4) {
                        isCrying = true
                        cryStart = System.currentTimeMillis()
                        levelSum = 0f; levelCount = 0; screamPeak = 0f
                        started = true
                    } else if (isCrying && hits == 0) {
                        isCrying = false
                        val secs = ((System.currentTimeMillis() - cryStart) / 1000).toInt()
                        ended = Triple(cryStart, secs, rate(levelSum / levelCount.coerceAtLeast(1), screamPeak, quietBefore))
                    }
                    if (isCrying) {
                        levelSum += lvl; levelCount++
                        screamPeak = maxOf(screamPeak, scream)
                    } else {
                        quietBefore = lvl
                    }
                    val currentIntensity = if (isCrying && levelCount >= 3)
                        rate(levelSum / levelCount, screamPeak, quietBefore) else null
                    val secsNow = if (isCrying) ((System.currentTimeMillis() - cryStart) / 1000).toInt() else 0

                    withContext(Dispatchers.Main) {
                        cryScore = baby
                        level = lvl
                        crying = isCrying
                        crySeconds = secsNow
                        if (currentIntensity != null) intensity = currentIntensity
                        if (started) { intensity = null; buzz(); onCryStart?.invoke() }
                        ended?.let { onCryEnd?.invoke(it.first, it.second, it.third) }
                    }
                    delay(500)
                }
            }
        } catch (e: Exception) {
            error = "Couldn't start listening: ${e.message ?: "unknown error"}"
            stop()
        }
    }

    fun stop() {
        job?.cancel(); job = null
        try { record?.stop() } catch (_: Exception) {}
        record?.release(); record = null
        listening = false
        crying = false
        level = 0f
        cryScore = 0f
        crySeconds = 0
    }

    fun close() {
        stop()
        classifier?.close(); classifier = null
    }

    /**
     * A rough loudness/sharpness rating. It depends on how far the phone is from the baby,
     * so it is shown as a hint, never as a diagnosis.
     */
    private fun rate(avgLevel: Float, screamPeak: Float, quietBefore: Float): String {
        val sudden = avgLevel - quietBefore > 0.35f
        return when {
            screamPeak > 0.3f || (avgLevel > 0.75f && sudden) -> "Very intense"
            avgLevel > 0.55f -> "Strong"
            else -> "Mild"
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
        } catch (_: Exception) {}
    }
}
