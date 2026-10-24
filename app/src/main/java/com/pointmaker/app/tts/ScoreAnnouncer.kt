package com.pointmaker.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.pointmaker.app.audio.TtsRecoveryPolicy
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Pembungkus `android.speech.tts.TextToSpeech` untuk mengumumkan skor pertandingan.
 *
 * Kontrak antrean (mengikat, PRD §5 catatan teknis + §7.4/§7.5): satu reli menghasilkan beberapa
 * baris ucapan berurutan (skor, lalu pemenang set, lalu pemenang pertandingan). Baris PERTAMA
 * memakai `QUEUE_FLUSH` untuk membuang sisa suara reli sebelumnya, baris BERIKUTNYA memakai
 * `QUEUE_ADD` agar tidak terpotong. `pending` hanya menyimpan rangkaian TERAKHIR dan diurai tepat
 * sekali (di-set null sebelum `speakLines`), sehingga tombol yang ditekan cepat tidak menumpuk
 * ucapan.
 *
 * Kontrak pemulihan: `onInit` yang gagal dicoba ulang dengan jeda 1s / 2s / 5s (maksimal 3
 * percobaan per instance engine), lalu engine dibangun ulang. Pembangunan ulang otomatis dibatasi
 * `TtsRecoveryPolicy.MAX_AUTO_REBUILDS_PER_MATCH` per pertandingan; setelah budget habis
 * [isRecoveryExhausted] bernilai true dan pemulihan hanya lewat [retryNow] atau
 * [resetRecoveryBudget].
 *
 * Audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) diminta sebelum baris pertama diucapkan;
 * kehilangan focus menghentikan ucapan dan focus kembali mengurai `pending`. Di bawah API 26
 * dipakai jalur legacy `requestAudioFocus`. Kegagalan meminta focus tidak memblokir ucapan.
 *
 * Semua metode publik tidak melempar exception; engine yang rusak/tidak tersedia hanya membuat
 * pemanggilan menjadi no-op (PRD §7.9).
 */
class ScoreAnnouncer(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val utteranceCounter = AtomicLong(0L)

    private var engine: TextToSpeech? = null
    private var engineGeneration = 0
    private var initAttempts = 0
    private var rebuildsUsed = 0
    private var lastRecoverAtMillis = Long.MIN_VALUE / 2
    private var retryScheduled = false
    private var focusHeld = false
    private var focusRequest: AudioFocusRequest? = null

    @Volatile
    private var engineReady = false

    @Volatile
    private var indonesianAvailable = false

    @Volatile
    private var released = false

    @Volatile
    private var recoveryExhausted = false

    @Volatile
    private var focusAvailable = true

    @Volatile
    private var pending: List<String>? = null

    @Volatile
    private var statusRevisionValue = 0L

    init {
        synchronized(lock) { engine = createEngineLocked() }
    }

    val isEngineReady: Boolean get() = engineReady

    val isIndonesianAvailable: Boolean get() = indonesianAvailable

    val isRecoveryExhausted: Boolean get() = recoveryExhausted

    val statusRevision: Long get() = statusRevisionValue

    fun statusSnapshot(): VoiceStatus = VoiceStatus(
        engineReady = engineReady,
        languageAvailable = indonesianAvailable,
        recoveryExhausted = recoveryExhausted
    )

    private fun markStatusChanged() {
        synchronized(lock) { statusRevisionValue += 1 }
    }

    private fun createEngineLocked(): TextToSpeech? {
        val generation = ++engineGeneration
        initAttempts = 0
        return try {
            TextToSpeech(appContext) { status -> handleInit(generation, status) }
        } catch (error: Throwable) {
            Log.e(TAG, "TextToSpeech gagal dibuat", error)
            null
        }
    }

    private fun handleInit(generation: Int, status: Int) {
        if (released) return
        synchronized(lock) {
            if (released || generation != engineGeneration) return
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Inisialisasi TextToSpeech gagal: status=$status")
                startInitRetryLocked()
                return
            }
            val tts = engine ?: return
            try {
                configure(tts)
                engineReady = true
                recoveryExhausted = false
                initAttempts = 0
                cancelInitRetryLocked()
                markStatusChanged()
            } catch (error: Throwable) {
                Log.e(TAG, "Gagal menyiapkan bahasa TextToSpeech", error)
                startInitRetryLocked()
                return
            }
        }
        drainPending()
    }

    private fun configure(tts: TextToSpeech) {
        tts.setAudioAttributes(SPEECH_ATTRIBUTES)
        indonesianAvailable = isAvailable(tts.setLanguage(Locale("id", "ID")))
        if (!indonesianAvailable) {
            val fallback = tts.setLanguage(Locale.getDefault())
            if (!isAvailable(fallback)) {
                Log.w(TAG, "Locale default juga tidak tersedia: $fallback")
            }
        }
        tts.setOnUtteranceProgressListener(progressListener)
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
            recover("onError kode=$errorCode")
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
            armInitRetryIfNeeded()
            return
        }
        speakLines(lines)
    }

    fun prewarm() {
        if (released) return
        synchronized(lock) {
            if (released) return
            if (engine == null) engine = createEngineLocked()
        }
    }

    fun stop() {
        if (released) return
        stopEngineSpeech()
        abandonFocus()
    }

    /**
     * Coba lagi manual dari UI. Selalu boleh dipanggil: mereset status budget dan
     * [isRecoveryExhausted], membangun ulang engine sekali, lalu menguras `pending`.
     */
    fun retryNow() {
        if (released) return
        synchronized(lock) {
            if (released) return
            recoveryExhausted = false
            lastRecoverAtMillis = Long.MIN_VALUE / 2
            cancelInitRetryLocked()
            shutdownEngineLocked()
            engineReady = false
            val rebuilt = createEngineLocked()
            engine = rebuilt
            if (rebuilt == null) {
                recoveryExhausted = true
                markStatusChanged()
                return
            }
            startInitRetryLocked()
            markStatusChanged()
        }
    }

    /** Kembalikan budget rebuild ke nol, dipanggil saat pertandingan baru atau reset skor. */
    fun resetRecoveryBudget() {
        synchronized(lock) {
            rebuildsUsed = 0
            recoveryExhausted = false
            lastRecoverAtMillis = Long.MIN_VALUE / 2
            markStatusChanged()
        }
    }

    fun shutdown() {
        if (released) return
        synchronized(lock) {
            if (released) return
            released = true
            cancelInitRetryLocked()
            shutdownEngineLocked()
            engineReady = false
            pending = null
            markStatusChanged()
        }
        abandonFocus()
    }

    private fun speakLines(lines: List<String>) {
        if (released) return
        val tts = synchronized(lock) { engine }
        if (tts == null) {
            pending = lines
            recover("engine tidak tersedia")
            return
        }
        requestFocusIfNeeded()
        lines.forEachIndexed { index, line ->
            if (released) return
            if (line.isBlank()) return@forEachIndexed
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = "pm-${utteranceCounter.incrementAndGet()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            val result = try {
                tts.speak(line, mode, params, utteranceId)
            } catch (error: Throwable) {
                Log.e(TAG, "speak() melempar exception: id=$utteranceId", error)
                TextToSpeech.ERROR
            }
            if (result == TextToSpeech.ERROR) {
                Log.w(TAG, "speak() mengembalikan ERROR, mencoba recover")
                pending = lines
                recover("speak ERROR")
                return
            }
        }
    }

    /**
     * Bangun ulang engine sekali bila budget masih ada. Rate limit 1 detik mencegah loop
     * `onError` yang beruntun membakar budget dalam sekejap.
     */
    private fun recover(reason: String) {
        if (released) return
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (released) return
            if (now - lastRecoverAtMillis < RECOVER_RATE_LIMIT_MILLIS) {
                Log.w(TAG, "recover($reason) diabaikan: masih dalam jeda rate limit")
                return
            }
            lastRecoverAtMillis = now
            if (!TtsRecoveryPolicy.shouldAutoRebuild(rebuildsUsed)) {
                engineReady = false
                recoveryExhausted = true
                markStatusChanged()
                Log.w(TAG, "recover($reason) gagal: budget rebuild habis")
                return
            }
            rebuildsUsed = TtsRecoveryPolicy.nextRebuildCount(rebuildsUsed)
            Log.w(TAG, "recover($reason): membangun ulang engine ke-$rebuildsUsed")
            cancelInitRetryLocked()
            shutdownEngineLocked()
            engineReady = false
            val rebuilt = createEngineLocked()
            engine = rebuilt
            recoveryExhausted = rebuilt == null &&
                !TtsRecoveryPolicy.shouldAutoRebuild(rebuildsUsed)
            if (rebuilt == null) startInitRetryLocked()
            markStatusChanged()
        }
    }

    private fun armInitRetryIfNeeded() {
        synchronized(lock) {
            if (released || retryScheduled || recoveryExhausted) return
            if (engine == null) {
                recover("engine tidak tersedia")
                return
            }
            startInitRetryLocked()
        }
    }

    private fun startInitRetryLocked() {
        if (released || retryScheduled) return
        retryScheduled = true
        initAttempts = 0
        val delay = TtsRecoveryPolicy.delayMillisForAttempt(0)
        Log.w(TAG, "Jadwal pemeriksaan inisialisasi dalam ${delay}ms")
        mainHandler.postDelayed(initRetryRunnable, delay)
    }

    private fun cancelInitRetryLocked() {
        retryScheduled = false
        initAttempts = 0
        mainHandler.removeCallbacks(initRetryRunnable)
    }

    private val initRetryRunnable: Runnable = Runnable {
        if (released) return@Runnable
        synchronized(lock) { retryScheduled = false }
        if (engineReady) return@Runnable
        val attemptIndex: Int
        val engineMissing: Boolean
        synchronized(lock) {
            if (released) return@Runnable
            attemptIndex = initAttempts
            engineMissing = engine == null
        }
        if (engineMissing) {
            recover("engine tidak tersedia")
            return@Runnable
        }
        if (!TtsRecoveryPolicy.shouldRetryInit(attemptIndex + 1)) {
            recover("init gagal")
            return@Runnable
        }
        val delay = TtsRecoveryPolicy.delayMillisForAttempt(attemptIndex + 1)
        synchronized(lock) {
            if (released) return@Runnable
            initAttempts = attemptIndex + 1
            retryScheduled = true
        }
        Log.w(TAG, "Inisialisasi belum siap, percobaan berikutnya dalam ${delay}ms")
        mainHandler.postDelayed(initRetryRunnable, delay)
    }

    private fun drainPending() {
        val lines = synchronized(lock) {
            if (released) return
            val stored = pending
            pending = null
            stored
        } ?: return
        speakLines(lines)
    }

    private fun stopEngineSpeech() {
        val tts = synchronized(lock) { engine }
        try {
            tts?.stop()
        } catch (error: Throwable) {
            Log.e(TAG, "stop() gagal", error)
        }
    }

    private fun shutdownEngineLocked() {
        val current = engine
        engine = null
        if (current == null) return
        try {
            current.stop()
        } catch (error: Throwable) {
            Log.e(TAG, "stop() gagal saat shutdown", error)
        }
        try {
            current.shutdown()
        } catch (error: Throwable) {
            Log.e(TAG, "shutdown() gagal", error)
        }
    }

    private fun requestFocusIfNeeded() {
        if (focusHeld && focusAvailable) return
        val manager = audioManager
        if (manager == null) {
            Log.w(TAG, "AudioManager tidak tersedia, ucapan tetap dicoba")
            return
        }
        val granted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requestFocusModern(manager)
            } else {
                requestFocusLegacy(manager)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "requestAudioFocus gagal", error)
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        if (granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusHeld = true
            focusAvailable = true
        } else {
            Log.w(TAG, "Audio focus tidak diberikan, ucapan tetap dicoba")
        }
    }

    private fun requestFocusModern(manager: AudioManager): Int {
        val request = focusRequest ?: AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(SPEECH_ATTRIBUTES)
            .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
            .setWillPauseWhenDucked(false)
            .build()
            .also { focusRequest = it }
        return manager.requestAudioFocus(request)
    }

    @Suppress("DEPRECATION")
    private fun requestFocusLegacy(manager: AudioManager): Int = manager.requestAudioFocus(
        focusChangeListener,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    )

    private fun abandonFocus() {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            } else {
                abandonFocusLegacy(manager)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "abandonAudioFocus gagal", error)
        }
        focusHeld = false
    }

    @Suppress("DEPRECATION")
    private fun abandonFocusLegacy(manager: AudioManager) {
        manager.abandonAudioFocus(focusChangeListener)
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusAvailable = false
                focusHeld = false
                stopEngineSpeech()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusAvailable = true
                drainPending()
                if (!engineReady) recover("audio focus kembali")
            }
            else -> Unit
        }
    }

    private companion object {
        const val TAG = "ScoreAnnouncer"
        const val RECOVER_RATE_LIMIT_MILLIS = 1_000L

        val SPEECH_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}

data class VoiceStatus(
    val engineReady: Boolean,
    val languageAvailable: Boolean,
    val recoveryExhausted: Boolean
)
