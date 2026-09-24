package com.pointmaker.app.wear

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class WearHaptics(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (error: Throwable) {
        Log.e(TAG, "Vibrator tidak dapat diakses", error)
        null
    }

    private val available: Boolean = try {
        vibrator?.hasVibrator() == true
    } catch (error: Throwable) {
        Log.e(TAG, "hasVibrator() gagal", error)
        false
    }

    fun pointTick() = fire(VibrationEffect.EFFECT_TICK)

    fun emphasis() = fire(VibrationEffect.EFFECT_DOUBLE_CLICK)

    fun undoTick() = fire(VibrationEffect.EFFECT_CLICK)

    private fun fire(effect: Int) {
        if (!available) return
        val device = vibrator ?: return
        try {
            device.vibrate(VibrationEffect.createPredefined(effect))
        } catch (error: Throwable) {
            Log.e(TAG, "vibrate() gagal", error)
        }
    }

    private companion object {
        const val TAG = "WearHaptics"
    }
}
