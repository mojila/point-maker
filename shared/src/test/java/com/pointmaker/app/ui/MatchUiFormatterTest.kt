package com.pointmaker.app.ui

import com.pointmaker.app.domain.MatchRules
import com.pointmaker.app.domain.MatchState
import com.pointmaker.app.domain.ScoringPreset
import com.pointmaker.app.domain.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchUiFormatterTest {

    @Test
    fun setHeaderShowsCurrentSet() {
        assertEquals("Set ke-1", MatchUiFormatter.setHeader(MatchState(currentSet = 1)))
        assertEquals("Set ke-2", MatchUiFormatter.setHeader(MatchState(currentSet = 2)))
        assertEquals("Set ke-3", MatchUiFormatter.setHeader(MatchState(currentSet = 3)))
    }

    @Test
    fun scoreTextRendersDigits() {
        assertEquals("0", MatchUiFormatter.scoreText(0))
        assertEquals("21", MatchUiFormatter.scoreText(21))
        assertEquals("30", MatchUiFormatter.scoreText(30))
    }

    @Test
    fun canAddPointTrueDuringPlay() {
        assertTrue(MatchUiFormatter.canAddPoint(MatchState(preset = ScoringPreset.TWENTY_ONE, leftPoints = 20, rightPoints = 19)))
    }

    @Test
    fun canAddPointFalseAfterSetEnds() {
        val finished = MatchRules.addPoint(MatchState(preset = ScoringPreset.TWENTY_ONE, leftPoints = 20, rightPoints = 18), Side.LEFT)

        assertTrue(MatchRules.isSetFinished(finished))
        assertFalse(MatchUiFormatter.canAddPoint(finished))
    }

    @Test
    fun canAddPointTrueAfterStartingNextSet() {
        val firstSet = MatchRules.addPoint(MatchState(preset = ScoringPreset.TWENTY_ONE, leftPoints = 20, rightPoints = 18), Side.LEFT)
        val secondSet = MatchRules.startNextSet(firstSet)

        assertEquals(2, secondSet.currentSet)
        assertTrue(MatchUiFormatter.canAddPoint(secondSet))
    }

    @Test
    fun canAddPointFalseAfterMatchEnds() {
        val firstSet = MatchRules.addPoint(MatchState(preset = ScoringPreset.TWENTY_ONE, leftPoints = 20, rightPoints = 10), Side.LEFT)
        val secondSet = MatchRules.addPoint(
            MatchRules.startNextSet(firstSet),
            Side.LEFT
        ).let { started -> (1..20).fold(started) { acc, _ -> MatchRules.addPoint(acc, Side.LEFT) } }

        assertTrue(MatchRules.isGameFinished(secondSet))
        assertFalse(MatchUiFormatter.canAddPoint(secondSet))
    }

    @Test
    fun canStartNextSetOnlyBetweenSets() {
        val finished = MatchRules.addPoint(MatchState(preset = ScoringPreset.TWENTY_ONE, leftPoints = 20, rightPoints = 18), Side.LEFT)
        assertTrue(MatchUiFormatter.canStartNextSet(finished))
        assertFalse(MatchUiFormatter.canStartNextSet(MatchState()))
    }

    @Test
    fun setMarkersReflectCompletedSet() {
        val afterFirstSet = MatchRules.addPoint(MatchState(preset = ScoringPreset.TWENTY_ONE, leftPoints = 20, rightPoints = 18), Side.LEFT)
        val secondSet = MatchRules.startNextSet(afterFirstSet)

        val markers = MatchUiFormatter.setMarkers(secondSet)

        assertEquals(3, markers.size)
        assertEquals(SetMarkerState.WON_LEFT, markers[0].state)
        assertEquals(21 to 18, markers[0].points)
        assertEquals(SetMarkerState.CURRENT, markers[1].state)
        assertEquals(SetMarkerState.PENDING, markers[2].state)
        assertEquals("21", MatchUiFormatter.setMarkerText(markers[0]))
    }

    @Test
    fun setMarkersMarkRightWin() {
        val afterFirstSet = MatchRules.addPoint(MatchState(preset = ScoringPreset.TWENTY_ONE, leftPoints = 15, rightPoints = 20), Side.RIGHT)

        val markers = MatchUiFormatter.setMarkers(afterFirstSet)

        assertEquals(SetMarkerState.WON_RIGHT, markers[0].state)
        assertEquals(15 to 21, markers[0].points)
        assertEquals(SetMarkerState.PENDING, markers[1].state)
    }

    @Test
    fun serveIndicatorCoversBothSides() {
        assertEquals("Servis: Kiri", MatchUiFormatter.serveIndicator(MatchState(servingSide = Side.LEFT)))
        assertEquals("Servis: Kanan", MatchUiFormatter.serveIndicator(MatchState(servingSide = Side.RIGHT)))
    }

    @Test
    fun serveIndicatorUsesCustomLabels() {
        val state = MatchState(servingSide = Side.RIGHT)

        assertEquals("Servis: Andi", MatchUiFormatter.serveIndicator(state, "Andi"))
    }

    @Test
    fun accessibilityDescriptionsStayIndonesian() {
        assertEquals("Tambah poin Kiri", MatchUiFormatter.addPointDescription("Kiri"))
        assertEquals("Skor Kiri tujuh belas", MatchUiFormatter.scoreDescription("Kiri", 17))
        assertEquals("Reset pertandingan", MatchUiFormatter.resetDescription())
        assertEquals("Batalkan poin terakhir", MatchUiFormatter.undoDescription())
        assertEquals("Buka pengaturan", MatchUiFormatter.settingsDescription())
    }

    @Test
    fun presetLabelFollowsActivePreset() {
        assertEquals("15 poin", MatchUiFormatter.presetLabel(MatchState()))
        assertEquals("21 poin", MatchUiFormatter.presetLabel(MatchState(preset = ScoringPreset.TWENTY_ONE)))
    }

    @Test
    fun presetSelectorCopyIsIndonesian() {
        assertEquals("Aturan skor", MatchUiFormatter.presetSelectorTitle())
        assertEquals("15 poin (best of 3)", MatchUiFormatter.presetOptionLabel(ScoringPreset.FIFTEEN))
        assertEquals("21 poin (best of 3)", MatchUiFormatter.presetOptionLabel(ScoringPreset.TWENTY_ONE))
    }

    @Test
    fun presetLockedHintExplainsReset() {
        assertEquals(
            "Aturan terkunci selama pertandingan berjalan. Reset untuk mengganti.",
            MatchUiFormatter.presetLockedHint()
        )
    }

    @Test
    fun presetChangeConfirmationCopyIsIndonesian() {
        assertEquals("Ganti aturan skor?", MatchUiFormatter.presetChangeConfirmTitle())
        assertEquals(
            "Mengganti aturan akan mereset skor pertandingan. Lanjutkan?",
            MatchUiFormatter.presetChangeConfirmMessage()
        )
        assertEquals("Ganti dan reset", MatchUiFormatter.presetChangeConfirmAccept())
        assertEquals("Batal", MatchUiFormatter.presetChangeConfirmDismiss())
    }

    @Test
    fun presetHeaderLabelCombinesBestOfAndPreset() {
        assertEquals("Best of 3 \u00B7 15 poin", MatchUiFormatter.presetHeaderLabel(MatchState()))
        assertEquals(
            "Best of 3 \u00B7 21 poin",
            MatchUiFormatter.presetHeaderLabel(MatchState(preset = ScoringPreset.TWENTY_ONE))
        )
        assertEquals("Aturan skor: 15 poin", MatchUiFormatter.presetHeaderDescription(MatchState()))
    }

    @Test
    fun voiceRetryCopyIsIndonesian() {
        assertEquals("Coba lagi", MatchUiFormatter.voiceRetryLabel())
        assertEquals("Coba pulihkan suara", MatchUiFormatter.voiceRetryDescription())
    }
}
