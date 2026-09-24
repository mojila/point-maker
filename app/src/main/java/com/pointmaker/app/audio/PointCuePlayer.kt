package com.pointmaker.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.pointmaker.app.R

class PointCuePlayer(context: Context) {

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
        try {
            val current = player
            if (current == null) {
                val created = MediaPlayer.create(appContext, R.raw.point_cue)
                if (created == null) {
                    Log.e(TAG, "Aset point_cue tidak dapat dimuat")
                    complete(token)
                    return
                }
                created.setOnCompletionListener { complete(pendingToken) }
                created.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    complete(pendingToken)
                    true
                }
                player = created
                created.start()
            } else {
                current.seekTo(0)
                current.start()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "play() gagal", error)
            complete(token)
        }
    }

    fun release() {
        if (released) return
        released = true
        generation++
        pendingComplete = null
        val current = player
        player = null
        try {
            current?.release()
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
        const val TAG = "PointCuePlayer"
    }
}
