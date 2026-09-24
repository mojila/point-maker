package com.pointmaker.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCueSequencerTest {

    private class Recorder {
        val cueCompletions = mutableListOf<() -> Unit>()
        val spoken = mutableListOf<List<String>>()

        fun sequencer() = ScoreCueSequencer(
            playCue = { onComplete -> cueCompletions.add(onComplete) },
            speak = { texts -> spoken.add(texts) }
        )
    }

    @Test
    fun `onPoint memicu playCue lalu speak saat selesai`() {
        val recorder = Recorder()
        val sequencer = recorder.sequencer()

        sequencer.onPoint(listOf("satu - nol"))

        assertEquals(1, recorder.cueCompletions.size)
        assertTrue(recorder.spoken.isEmpty())

        recorder.cueCompletions.single().invoke()

        assertEquals(1, recorder.spoken.size)
        assertEquals(listOf("satu - nol"), recorder.spoken.single())
    }

    @Test
    fun `token lama dibatalkan saat token baru datang`() {
        val recorder = Recorder()
        val sequencer = recorder.sequencer()

        sequencer.onPoint(listOf("satu - nol"))
        val first = recorder.cueCompletions[0]
        sequencer.onPoint(listOf("dua - nol"))
        val second = recorder.cueCompletions[1]

        first.invoke()
        assertTrue(recorder.spoken.isEmpty())

        second.invoke()
        assertEquals(1, recorder.spoken.size)
        assertEquals(listOf("dua - nol"), recorder.spoken.single())
    }

    @Test
    fun `onComplete ganda tidak menggandakan speak`() {
        val recorder = Recorder()
        val sequencer = recorder.sequencer()

        sequencer.onPoint(listOf("tiga - nol"))
        val complete = recorder.cueCompletions.single()

        complete.invoke()
        complete.invoke()

        assertEquals(1, recorder.spoken.size)
    }

    @Test
    fun `onPlain tidak memanggil playCue`() {
        val recorder = Recorder()
        val sequencer = recorder.sequencer()

        sequencer.onPlain(listOf("set dua dimulai"))

        assertTrue(recorder.cueCompletions.isEmpty())
        assertEquals(1, recorder.spoken.size)
        assertEquals(listOf("set dua dimulai"), recorder.spoken.single())
    }

    @Test
    fun `onPlain kosong tidak mengucapkan apa pun`() {
        val recorder = Recorder()
        val sequencer = recorder.sequencer()

        sequencer.onPlain(emptyList())

        assertTrue(recorder.cueCompletions.isEmpty())
        assertTrue(recorder.spoken.isEmpty())
    }

    @Test
    fun `onPlain membatalkan token point yang belum selesai`() {
        val recorder = Recorder()
        val sequencer = recorder.sequencer()

        sequencer.onPoint(listOf("satu - nol"))
        val stale = recorder.cueCompletions.single()
        sequencer.onPlain(listOf("set dua dimulai"))

        stale.invoke()

        assertEquals(1, recorder.spoken.size)
        assertEquals(listOf("set dua dimulai"), recorder.spoken.single())
    }
}
