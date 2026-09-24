package com.pointmaker.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreFormatterTest {

    private fun stateAt(left: Int, right: Int, setsLeft: Int = 0, setsRight: Int = 0, currentSet: Int = 1) =
        MatchState(
            currentSet = currentSet,
            leftPoints = left,
            rightPoints = right,
            setsWonLeft = setsLeft,
            setsWonRight = setsRight
        )

    @Test
    fun `angka dalam kata bahasa indonesia 0 sampai 30`() {
        assertEquals("nol", ScoreFormatter.number(0))
        assertEquals("tiga", ScoreFormatter.number(3))
        assertEquals("sebelas", ScoreFormatter.number(11))
        assertEquals("lima belas", ScoreFormatter.number(15))
        assertEquals("tujuh belas", ScoreFormatter.number(17))
        assertEquals("dua puluh", ScoreFormatter.number(20))
        assertEquals("dua puluh satu", ScoreFormatter.number(21))
        assertEquals("dua puluh sembilan", ScoreFormatter.number(29))
        assertEquals("tiga puluh", ScoreFormatter.number(30))
    }

    @Test
    fun `poin biasa tujuh belas lima belas`() {
        val before = stateAt(16, 15)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals("tujuh belas, lima belas", ScoreFormatter.pointCall(after))
    }

    @Test
    fun `skor nol diucapkan nol tiga`() {
        assertEquals("nol, tiga", ScoreFormatter.pointCall(stateAt(0, 3)))
    }

    @Test
    fun `deuce diucapkan dua puluh sama`() {
        val before = stateAt(19, 20)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals("dua puluh sama. Deuce", ScoreFormatter.pointCall(after))
        assertEquals("dua puluh sama. Deuce", ScoreFormatter.announcePoint(before, after, Side.LEFT))
    }

    @Test
    fun `set point untuk kiri`() {
        val before = stateAt(19, 18)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals("dua puluh, delapan belas. Set point untuk kiri", ScoreFormatter.pointCall(after))
    }

    @Test
    fun `match point untuk kanan`() {
        val before = stateAt(18, 20, setsRight = 1)
        val after = MatchRules.addPoint(before, Side.RIGHT)

        assertEquals(
            "delapan belas, dua puluh satu. Match point untuk kanan",
            ScoreFormatter.announcePoint(before, after, Side.RIGHT)
        )
        assertEquals(
            "delapan belas, dua puluh satu. Match point untuk kanan",
            ScoreFormatter.announcements(before, after, Side.RIGHT).first()
        )
    }

    @Test
    fun `set berakhir diucapkan dengan nama set dan skor tanpa koma`() {
        assertEquals(
            "Set pertama untuk kiri, dua puluh satu delapan belas",
            ScoreFormatter.setFinished(Side.LEFT, 1, 21, 18)
        )
        assertEquals("Set kedua, mulai", ScoreFormatter.nextSetStart(2))
        assertEquals(
            "Pertandingan selesai. Pemenang: kiri, dua set nol",
            ScoreFormatter.matchFinished(Side.LEFT, 2, 0)
        )
    }

    @Test
    fun `batas 30 diucapkan tiga puluh dua puluh sembilan`() {
        val before = stateAt(29, 29)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals(
            "tiga puluh dua puluh sembilan. Set point untuk kiri",
            ScoreFormatter.announcePoint(before, after, Side.LEFT)
        )
    }

    @Test
    fun `set pointer saat mendekati akhir set 19-20`() {
        val before = stateAt(19, 19)
        val after = MatchRules.addPoint(before, Side.RIGHT)

        assertEquals("sembilan belas, dua puluh. Set point untuk kanan", ScoreFormatter.pointCall(after))
    }

    @Test
    fun `anouncements mengembalikan urutan skor lalu pemenang set`() {
        val before = stateAt(20, 17)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals(
            listOf(
                "dua puluh satu, tujuh belas. Set point untuk kiri",
                "Set pertama untuk kiri, dua puluh satu tujuh belas"
            ),
            ScoreFormatter.announcements(before, after, Side.LEFT)
        )
    }

    @Test
    fun `anouncements pertandingan berakhir menyertakan pemenang`() {
        val before = stateAt(20, 10, setsLeft = 1, currentSet = 2)
        val after = MatchRules.addPoint(before, Side.LEFT)

        val texts = ScoreFormatter.announcements(before, after, Side.LEFT)

        assertEquals(3, texts.size)
        assertEquals("dua puluh satu, sepuluh. Match point untuk kiri", texts[0])
        assertEquals("Set kedua untuk kiri, dua puluh satu sepuluh", texts[1])
        assertEquals("Pertandingan selesai. Pemenang: kiri, dua set nol", texts[2])
    }

    @Test
    fun `prioritas match point mengalahkan set point`() {
        val state = stateAt(20, 18, setsLeft = 1)
        val announcement = Announcement.forPoint(stateAt(19, 18, setsLeft = 1), state, Side.LEFT)

        assertEquals(Announcement.Priority.MATCH_POINT, announcement.priority)
        assertEquals(1, announcement.texts.size)
    }

    @Test
    fun `urutan sisi selalu kiri lalu kanan`() {
        assertEquals("lima, dua puluh satu", ScoreFormatter.scorePair(5, 21))
        assertEquals("nol, tiga puluh", ScoreFormatter.scorePair(0, 30))
    }

    @Test
    fun `skor seri tidak melempar exception dan memakai sisi servis`() {
        val deuceState = stateAt(20, 20)

        assertEquals("dua puluh sama. Deuce", ScoreFormatter.pointCall(deuceState))
        assertEquals(Side.LEFT, ScoreFormatter.scoringSideOf(stateAt(21, 21)))
        assertEquals("dua puluh satu, dua puluh satu", ScoreFormatter.pointCall(stateAt(21, 21)))
    }

    @Test
    fun `angka di luar rentang dijepit tanpa melempar exception`() {
        assertEquals("nol", ScoreFormatter.number(-3))
        assertEquals("tiga puluh", ScoreFormatter.number(45))
    }
}
