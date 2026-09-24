package com.pointmaker.app.audio

class ScoreCueSequencer(
    private val playCue: (onComplete: () -> Unit) -> Unit,
    private val speak: (List<String>) -> Unit
) {

    private var generation = 0L
    private var spokenGeneration = -1L

    fun onPoint(texts: List<String>) {
        val token = ++generation
        playCue {
            if (token != generation) return@playCue
            if (spokenGeneration == token) return@playCue
            spokenGeneration = token
            speak(texts)
        }
    }

    fun onPlain(texts: List<String>) {
        generation++
        if (texts.isEmpty()) return
        speak(texts)
    }
}
