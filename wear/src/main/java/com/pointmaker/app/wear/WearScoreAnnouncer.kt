package com.pointmaker.app.wear

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Pembungkus `android.speech.tts.TextToSpeech` untuk jam.
 *
 * Kontrak antrean sama dengan versi HP: baris PERTAMA satu reli memakai `QUEUE_FLUSH`, baris
 * BERIKUTNYA `QUEUE_ADD`. Tidak ada ucapan sebelum `onInit` sukses (engine Wear OS butuh ~10 detik
 * siap setelah boot, R7); rangkaian terakhir disimpan di `pending`. Semua metode publik tidak
 * melempar exception.
 *
 * Audio diarahkan lewat `AudioAttributes` eksplisit (`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` +
 * `CONTENT_TYPE_SPEECH`) agar keluar lewat speaker jam, bukan tertahan sebagai media.
 */
class WearScoreAnnouncer(context: Context) {

    private val engine: TextToSpeech? = try {
        TextToSpeech(context.applicationContext) { status -> handleInit(status) }
    } catch (error: Throwable) {
        Log.e(TAG, "TextToSpeech gagal dibuat", error)
        null
    }

    @Volatile
    private var engineReady = false

    @Volatile
    private var indonesianAvailable = false

    @Volatile
    private var released = false

    @Volatile
    private var pending: List<String>? = null

    private val utteranceCounter = AtomicLong(0L)

    val isEngineReady: Boolean get() = engineReady
    val isIndonesianAvailable: Boolean get() = indonesianAvailable

    private fun handleInit(status: Int) {
        if (released) return
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "Inisialisasi TextToSpeech gagal: status=$status")
            return
        }
        val tts = engine ?: return
        try {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            indonesianAvailable = isAvailable(tts.setLanguage(Locale("id", "ID")))
            if (!indonesianAvailable) {
                val fallback = tts.setLanguage(Locale.getDefault())
                if (!isAvailable(fallback)) {
                    Log.w(TAG, "Locale default juga tidak tersedia: $fallback")
                }
            }
            tts.setOnUtteranceProgressListener(progressListener)
            engineReady = true
        } catch (error: Throwable) {
            Log.e(TAG, "Gagal menyiapkan bahasa TextToSpeech", error)
            return
        }
        pending?.let { lines ->
            pending = null
            speakLines(lines)
        }
    }

    private fun isAvailable(result: Int): Boolean = when (result) {
        TextToSpeech.LANG_AVAILABLE,
        TextToSpeech.LANG_COUNTRY_AVAILABLE,
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> true
        TextToSpeech.LANG_MISSING_DATA,
        TextToSpeech.LANG_NOT_SUPPORTED -> false
        else -> false
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = Unit

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            Log.e(TAG, "Ucapan gagal: id=$utteranceId")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.e(TAG, "Ucapan gagal: id=$utteranceId, kode=$errorCode")
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit
    }

    fun speak(lines: List<String>) {
        if (released || lines.isEmpty()) return
        if (!engineReady) {
            pending = lines
            return
        }
        speakLines(lines)
    }

    fun prewarm() {
        if (released) return
        engine
    }

    fun stop() {
        if (released) return
        try {
            engine?.stop()
        } catch (error: Throwable) {
            Log.e(TAG, "stop() gagal", error)
        }
    }

    private fun speakLines(lines: List<String>) {
        val tts = engine ?: return
        lines.forEachIndexed { index, line ->
            if (released || line.isBlank()) return@forEachIndexed
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = "pm-wear-${utteranceCounter.incrementAndGet()}"
            val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
            try {
                tts.speak(line, mode, params, utteranceId)
            } catch (error: Throwable) {
                Log.e(TAG, "speak() gagal: id=$utteranceId", error)
            }
        }
    }

    fun shutdown() {
        if (released) return
        released = true
        engineReady = false
        pending = null
        try {
            engine?.stop()
        } catch (error: Throwable) {
            Log.e(TAG, "stop() gagal saat shutdown", error)
        }
        try {
            engine?.shutdown()
        } catch (error: Throwable) {
            Log.e(TAG, "shutdown() gagal", error)
        }
    }

    private companion object {
        const val TAG = "WearScoreAnnouncer"
    }
}
