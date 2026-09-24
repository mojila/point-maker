package com.pointmaker.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementTextTest {

    @Test
    fun voiceStatusWhenEngineNotReady() {
        assertEquals("Suara tidak tersedia", AnnouncementText.voiceStatus(languageAvailable = true, engineReady = false))
        assertEquals("Suara tidak tersedia", AnnouncementText.voiceStatus(languageAvailable = false, engineReady = false))
    }

    @Test
    fun voiceStatusWhenIndonesianUnavailable() {
        assertEquals(
            "Suara Bahasa Indonesia tidak tersedia",
            AnnouncementText.voiceStatus(languageAvailable = false, engineReady = true)
        )
    }

    @Test
    fun voiceStatusWhenActive() {
        assertEquals("Suara aktif", AnnouncementText.voiceStatus(languageAvailable = true, engineReady = true))
    }

    @Test
    fun voiceStatusReportsMutedWhenUserDisabledVoice() {
        // Bisu yang disengaja pengguna harus berbeda dari TTS rusak, dan menang atas status engine.
        assertEquals(
            "Suara mati",
            AnnouncementText.voiceStatus(languageAvailable = true, engineReady = true, voiceEnabled = false)
        )
        assertEquals(
            "Suara mati",
            AnnouncementText.voiceStatus(languageAvailable = false, engineReady = false, voiceEnabled = false)
        )
    }

    @Test
    fun statusBannerPrefersMatchFinished() {
        val finished = MatchState(setsWonLeft = 2, leftPoints = 21, rightPoints = 19, matchFinished = true)
        assertEquals("Pertandingan selesai", AnnouncementText.statusBanner(finished, Side.RIGHT))
    }

    @Test
    fun statusBannerReportsSetFinishedBeforePointStates() {
        val setFinished = MatchState(currentSet = 2, leftPoints = 21, rightPoints = 19, setsWonLeft = 1)
        assertEquals("Set kedua untuk kiri", AnnouncementText.statusBanner(setFinished, Side.RIGHT))
    }

    @Test
    fun statusBannerReportsMatchPointBeforeSetPoint() {
        val matchPoint = MatchState(leftPoints = 20, rightPoints = 5, setsWonLeft = 1)
        assertEquals("Match point", AnnouncementText.statusBanner(matchPoint, Side.LEFT))
    }

    @Test
    fun statusBannerReportsSetPointBeforeDeuce() {
        val setPoint = MatchState(leftPoints = 20, rightPoints = 18)
        assertEquals("Set point", AnnouncementText.statusBanner(setPoint, Side.LEFT))
    }

    @Test
    fun statusBannerReportsDeuce() {
        val deuce = MatchState(leftPoints = 20, rightPoints = 20)
        assertEquals("Deuce", AnnouncementText.statusBanner(deuce, Side.LEFT))
    }

    @Test
    fun statusBannerIsEmptyOnPlainScore() {
        assertEquals("", AnnouncementText.statusBanner(MatchState(leftPoints = 3, rightPoints = 2), Side.LEFT))
    }

    @Test
    fun controlLabelsContainEveryRequiredLabel() {
        val labels = AnnouncementText.controlLabels()
        listOf(
            "Tambah poin kiri",
            "Tambah poin kanan",
            "Undo",
            "Reset",
            "Pengaturan",
            "Suara aktif"
        ).forEach { expected ->
            assertTrue("label '$expected' tidak ditemukan", labels.contains(expected))
        }
    }
}
