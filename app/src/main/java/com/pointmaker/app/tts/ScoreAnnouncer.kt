package com.pointmaker.app.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Pembungkus `android.speech.tts.TextToSpeech` untuk mengumumkan skor pertandingan.
 *
 * Kontrak antrean (mengikat, PRD §5 catatan teknis + §7.4/§7.5):
 * satu reli menghasilkan beberapa baris ucapan berurutan (skor, lalu pemenang set, lalu
 * pemenang pertandingan). Baris PERTAMA memakai `QUEUE_FLUSH` untuk membuang sisa suara reli
 * sebelumnya, baris BERIKUTNYA memakai `QUEUE_ADD` agar tidak terpotong. Baris tidak digabung
 * menjadi satu ucapan.
 *
 * Semua metode publik tidak melempar exception; engine yang rusak/tidak tersedia hanya membuat
 * pemanggilan menjadi no-op (PRD §7.9).
 */
class ScoreAnnouncer(context: Context) {

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

    /**
     * Ucapkan rangkaian baris dari satu reli.
     *
     * Bila engine belum siap, hanya rangkaian terakhir yang disimpan dan diucapkan setelah
     * `onInit` sukses; reli sebelumnya yang belum sempat diucapkan boleh hilang.
     */
    fun speak(lines: List<String>) {
        if (released || lines.isEmpty()) return
        if (!engineReady) {
            pending = lines
            return
        }
        speakLines(lines)
    }

    private fun speakLines(lines: List<String>) {
        val tts = engine ?: return
        lines.forEachIndexed { index, line ->
            if (released || line.isBlank()) return@forEachIndexed
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = "pm-${utteranceCounter.incrementAndGet()}"
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

    fun stop() {
        if (released) return
        try {
            engine?.stop()
        } catch (error: Throwable) {
            Log.e(TAG, "stop() gagal", error)
        }
    }

    private companion object {
        const val TAG = "ScoreAnnouncer"
    }
}
