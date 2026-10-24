package com.pointmaker.app.wear

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

/**
 * Pemutar bunyi cue satu tembakan untuk jam.
 *
 * MediaPlayer yang sudah masuk status error tidak boleh dipakai ulang: `seekTo`/`start` akan
 * melempar `IllegalStateException`. Karena itu player yang error langsung di-release dan di-null
 * kan, lalu reli berikutnya membuat player baru. Bila `start` tetap gagal setelah dibuat ulang,
 * callback `onComplete` dipanggil agar rangkaian suara tidak tertahan.
 */
class WearPointCuePlayer(context: Context) {

    private val appContext = context.applicationContext

    private var player: MediaPlayer? = null
    private var generation = 0L
    private var pendingToken = 0L
    private var pendingComplete: (() -> Unit)? = null
    private var notifiedToken = -1L
    private var released = false

    fun play(onComplete: () -> Unit) {
        if (released) {
            runCatching { onComplete() }
            return
        }
        val token = ++generation
        pendingToken = token
        pendingComplete = onComplete
        val current = player
        if (current == null) {
            if (!startFresh(token)) complete(token)
            return
        }
        if (startExisting(current, token)) return
        if (!startFresh(token)) complete(token)
    }

    private fun startExisting(current: MediaPlayer, token: Long): Boolean = try {
        bindListeners(current, token)
        current.seekTo(0)
        current.start()
        true
    } catch (error: RuntimeException) {
        Log.e(TAG, "seekTo/start gagal, player akan dibuat ulang", error)
        releaseCurrent()
        false
    }

    private fun bindListeners(target: MediaPlayer, token: Long) {
        target.setOnCompletionListener { complete(token) }
        target.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
            releaseCurrent()
            complete(token)
            true
        }
    }

    private fun startFresh(token: Long): Boolean {
        val created = try {
            MediaPlayer.create(appContext, R.raw.point_cue)
        } catch (error: RuntimeException) {
            Log.e(TAG, "MediaPlayer.create gagal", error)
            null
        }
        if (created == null) {
            Log.e(TAG, "Aset point_cue tidak dapat dimuat")
            return false
        }
        bindListeners(created, token)
        player = created
        return try {
            created.start()
            true
        } catch (error: RuntimeException) {
            Log.e(TAG, "start() gagal pada player baru", error)
            releaseCurrent()
            false
        }
    }

    fun release() {
        if (released) return
        released = true
        generation++
        pendingComplete = null
        releaseCurrent()
    }

    private fun releaseCurrent() {
        val current = player
        player = null
        if (current == null) return
        try {
            current.release()
        } catch (error: Throwable) {
            Log.e(TAG, "release() gagal", error)
        }
    }

    private fun complete(token: Long) {
        if (released) return
        if (token != pendingToken) return
        if (notifiedToken == token) return
        notifiedToken = token
        val callback = pendingComplete
        pendingComplete = null
        try {
            callback?.invoke()
        } catch (error: Throwable) {
            Log.e(TAG, "onComplete gagal", error)
        }
    }

    private companion object {
        const val TAG = "WearPointCuePlayer"
    }
}
