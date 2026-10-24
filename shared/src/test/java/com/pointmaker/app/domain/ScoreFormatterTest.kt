package com.pointmaker.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreFormatterTest {

    private fun stateAt(
        left: Int,
        right: Int,
        setsLeft: Int = 0,
        setsRight: Int = 0,
        currentSet: Int = 1,
        servingSide: Side = Side.LEFT
    ) =
        MatchState(
            currentSet = currentSet,
            leftPoints = left,
            rightPoints = right,
            setsWonLeft = setsLeft,
            setsWonRight = setsRight,
            servingSide = servingSide
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
    fun `skor nol diucapkan tiga nol`() {
        assertEquals("tiga, nol", ScoreFormatter.pointCall(stateAt(0, 3)))
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

        assertEquals(
            listOf("dua puluh, delapan belas. Set point untuk kiri"),
            Announcement.forPoint(before, after, Side.LEFT).texts
        )
    }

    @Test
    fun `poin penutup set oleh kanan tidak menyebut match point`() {
        val before = stateAt(18, 20, setsRight = 1)
        val after = MatchRules.addPoint(before, Side.RIGHT)

        assertEquals(
            listOf(
                "dua puluh satu, delapan belas",
                "Set pertama untuk kanan, dua puluh satu delapan belas",
                "Pertandingan selesai. Pemenang: kanan, dua set nol"
            ),
            Announcement.forPoint(before, after, Side.RIGHT).texts
        )
    }

    @Test
    fun `sufiks match point muncul saat set point tercipta bukan saat set ditutup`() {
        val before = stateAt(19, 18, setsLeft = 1)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals(
            listOf("dua puluh, delapan belas. Match point untuk kiri"),
            Announcement.forPoint(before, after, Side.LEFT).texts
        )
    }

    @Test
    fun `set berakhir diucapkan dengan nama set dan skor tanpa koma`() {
        assertEquals(
            "Set pertama untuk kiri, dua puluh satu delapan belas",
            ScoreFormatter.setFinished(Side.LEFT, 1, 21, 18)
        )
        assertEquals(
            "Set pertama untuk kanan, dua puluh satu delapan belas",
            ScoreFormatter.setFinished(Side.RIGHT, 1, 18, 21)
        )
        assertEquals("Set kedua, mulai", ScoreFormatter.nextSetStart(2))
        assertEquals(
            "Pertandingan selesai. Pemenang: kiri, dua set nol",
            ScoreFormatter.matchFinished(Side.LEFT, 2, 0)
        )
    }

    @Test
    fun `batas 30 diucapkan tanpa set point karena set selesai`() {
        val before = stateAt(29, 29)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals(
            listOf(
                "tiga puluh, dua puluh sembilan",
                "Set pertama untuk kiri, tiga puluh dua puluh sembilan"
            ),
            ScoreFormatter.announcements(before, after, Side.LEFT)
        )
    }

    @Test
    fun `set pointer saat mendekati akhir set 19-20`() {
        val before = stateAt(19, 19)
        val after = MatchRules.addPoint(before, Side.RIGHT)

        assertEquals("dua puluh, sembilan belas. Set point untuk kanan", ScoreFormatter.pointCall(after))
    }

    @Test
    fun `anouncements mengembalikan urutan skor lalu pemenang set`() {
        val before = stateAt(20, 17)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals(
            listOf(
                "dua puluh satu, tujuh belas",
                "Set pertama untuk kiri, dua puluh satu tujuh belas"
            ),
            ScoreFormatter.announcements(before, after, Side.LEFT)
        )
    }

    @Test
    fun `announcements akhir set memakai urutan pemenang lebih dulu`() {
        val before = stateAt(18, 20)
        val after = MatchRules.addPoint(before, Side.RIGHT)

        assertEquals(
            listOf(
                "dua puluh satu, delapan belas",
                "Set pertama untuk kanan, dua puluh satu delapan belas"
            ),
            ScoreFormatter.announcements(before, after, Side.RIGHT)
        )
    }

    @Test
    fun `anouncements pertandingan berakhir menyertakan pemenang`() {
        val before = stateAt(20, 10, setsLeft = 1, currentSet = 2)
        val after = MatchRules.addPoint(before, Side.LEFT)

        val texts = ScoreFormatter.announcements(before, after, Side.LEFT)

        assertEquals(3, texts.size)
        assertEquals("dua puluh satu, sepuluh", texts[0])
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
    fun `urutan sisi mengikuti sisi yang diberikan`() {
        assertEquals("lima, dua puluh satu", ScoreFormatter.scorePair(Side.LEFT, 5, 21))
        assertEquals("dua puluh satu, lima", ScoreFormatter.scorePair(Side.RIGHT, 5, 21))
        assertEquals("nol, tiga puluh", ScoreFormatter.scorePair(Side.LEFT, 0, 30))
        assertEquals("tiga puluh, nol", ScoreFormatter.scorePair(Side.RIGHT, 0, 30))
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

    @Test
    fun `baris 1 poin kiri 17-15 menjadi 18-15`() {
        val before = stateAt(17, 15)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals("delapan belas, lima belas", ScoreFormatter.announcements(before, after, Side.LEFT).single())
    }

    @Test
    fun `baris 2 poin kanan 5-8 menjadi 5-9`() {
        val before = stateAt(5, 8)
        val after = MatchRules.addPoint(before, Side.RIGHT)

        assertEquals("sembilan, lima", ScoreFormatter.announcements(before, after, Side.RIGHT).single())
    }

    @Test
    fun `baris 3 poin pertama set oleh kanan`() {
        val before = stateAt(0, 0)
        val after = MatchRules.addPoint(before, Side.RIGHT)

        assertEquals("satu, nol", ScoreFormatter.announcements(before, after, Side.RIGHT).single())
    }

    @Test
    fun `baris 4 poin pertama set oleh kiri`() {
        val before = stateAt(0, 0)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals("satu, nol", ScoreFormatter.announcements(before, after, Side.LEFT).single())
    }

    @Test
    fun `baris 5 set point kanan 19-19 menjadi 19-20`() {
        val before = stateAt(19, 19)
        val after = MatchRules.addPoint(before, Side.RIGHT)

        assertEquals(
            listOf("dua puluh, sembilan belas. Set point untuk kanan"),
            Announcement.forPoint(before, after, Side.RIGHT).texts
        )
    }

    @Test
    fun `baris 6 match point kanan 19-19 menjadi 19-20`() {
        val before = stateAt(19, 19, setsRight = 1)
        val after = MatchRules.addPoint(before, Side.RIGHT)

        assertEquals(
            listOf("dua puluh, sembilan belas. Match point untuk kanan"),
            Announcement.forPoint(before, after, Side.RIGHT).texts
        )
    }

    @Test
    fun `baris 7 deuce`() {
        assertEquals("dua puluh sama. Deuce", ScoreFormatter.pointCall(stateAt(20, 20), Side.LEFT))
    }

    @Test
    fun `baris 8 set point kiri setelah deuce`() {
        val before = stateAt(20, 20)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals(
            listOf("dua puluh satu, dua puluh. Set point untuk kiri"),
            Announcement.forPoint(before, after, Side.LEFT).texts
        )
    }

    @Test
    fun `baris 9 batas 30 kiri menang set`() {
        val before = stateAt(29, 29)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals("Set pertama untuk kiri, tiga puluh dua puluh sembilan", ScoreFormatter.setFinished(Side.LEFT, 1, 30, 29))
        assertEquals(
            "Set pertama untuk kiri, tiga puluh dua puluh sembilan",
            ScoreFormatter.announcements(before, after, Side.LEFT).last()
        )
    }

    @Test
    fun `baris 10 akhir set oleh kanan`() {
        val before = stateAt(18, 20)
        val after = MatchRules.addPoint(before, Side.RIGHT)

        assertEquals("Set pertama untuk kanan, dua puluh satu delapan belas", ScoreFormatter.setFinished(Side.RIGHT, 1, 18, 21))
        assertEquals(
            "Set pertama untuk kanan, dua puluh satu delapan belas",
            ScoreFormatter.announcements(before, after, Side.RIGHT).last()
        )
    }

    @Test
    fun `baris 11 akhir set oleh kiri`() {
        val before = stateAt(20, 19)
        val after = MatchRules.addPoint(before, Side.LEFT)

        assertEquals("Set pertama untuk kiri, dua puluh satu sembilan belas", ScoreFormatter.setFinished(Side.LEFT, 1, 21, 19))
        assertEquals(
            "Set pertama untuk kiri, dua puluh satu sembilan belas",
            ScoreFormatter.announcements(before, after, Side.LEFT).last()
        )
    }

    @Test
    fun `baris 12 akhir pertandingan tidak berubah`() {
        assertEquals(
            "Pertandingan selesai. Pemenang: kiri, dua set nol",
            ScoreFormatter.matchFinished(Side.LEFT, 2, 0)
        )
    }

    @Test
    fun `baris 13 ganti set tidak berubah`() {
        assertEquals("Set kedua, mulai", ScoreFormatter.nextSetStart(2))
    }

    @Test
    fun `baris 14 undo tidak menghasilkan ucapan`() {
        val before = stateAt(5, 8)
        val after = MatchRules.addPoint(before, Side.RIGHT)
        val undone = MatchRules.undo(after)

        assertEquals(before, undone)
        assertEquals(emptyList<String>(), ScoreFormatter.announcements(undone, undone, Side.RIGHT))
    }

    @Test
    fun `baris 15 reset tidak menghasilkan ucapan`() {
        val reset = MatchState()

        assertEquals(emptyList<String>(), ScoreFormatter.announcements(reset, reset, Side.LEFT))
        assertEquals(Announcement.NONE, Announcement.forPoint(reset, reset, Side.LEFT))
    }

    @Test
    fun `baris 16 seri lain tidak berubah`() {
        assertEquals("lima, lima", ScoreFormatter.pointCall(stateAt(5, 5), Side.LEFT))
        assertEquals("sembilan belas, sembilan belas", ScoreFormatter.pointCall(stateAt(19, 19), Side.RIGHT))
    }

    @Test
    fun `nol nol tanpa perubahan poin tidak menghasilkan ucapan`() {
        val state = stateAt(0, 0)

        assertEquals(emptyList<String>(), ScoreFormatter.announcements(state, state, Side.LEFT))
        assertEquals(Announcement.NONE, Announcement.forPoint(state, state, Side.LEFT))
    }

    @Test
    fun `poin pertama oleh sisi mana pun diucapkan satu nol`() {
        val start = stateAt(0, 0)

        assertEquals(
            "satu, nol",
            ScoreFormatter.announcements(start, MatchRules.addPoint(start, Side.LEFT), Side.LEFT).single()
        )
        assertEquals(
            "satu, nol",
            ScoreFormatter.announcements(start, MatchRules.addPoint(start, Side.RIGHT), Side.RIGHT).single()
        )
    }

    @Test
    fun `skor seri netral untuk kedua sisi servis`() {
        // Pada skor seri, sufiks set point/match point mustahil muncul: isSetPoint dan isMatchPoint
        // mensyaratkan selisih poin, sehingga fallback servingSide tidak dapat mengubah bunyi.
        listOf(Side.LEFT, Side.RIGHT).forEach { serving ->
            assertEquals("lima, lima", ScoreFormatter.pointCall(stateAt(5, 5, servingSide = serving)))
            assertEquals("dua puluh sama. Deuce", ScoreFormatter.pointCall(stateAt(20, 20, servingSide = serving)))
        }
    }

    @Test
    fun `arah urutan ditentukan argumen sisi bukan perbandingan skor`() {
        assertEquals("lima, dua puluh satu", ScoreFormatter.scorePair(Side.LEFT, 5, 21))
        assertEquals("dua puluh satu, lima", ScoreFormatter.scorePair(Side.RIGHT, 5, 21))
        assertEquals("tujuh, tujuh", ScoreFormatter.scorePair(Side.LEFT, 7, 7))
        assertEquals("tujuh, tujuh", ScoreFormatter.scorePair(Side.RIGHT, 7, 7))
    }

    @Test
    fun `jumlah kata panggilan poin tidak bertambah`() {        val before = stateAt(5, 8)
        val after = MatchRules.addPoint(before, Side.RIGHT)
        val plain = ScoreFormatter.announcements(before, after, Side.RIGHT).single()
        val plainBaseline = "${ScoreFormatter.number(5)}, ${ScoreFormatter.number(9)}"

        assertEquals(plainBaseline.split(" ").size, plain.split(" ").size)

        val setPointAfter = MatchRules.addPoint(stateAt(19, 19), Side.RIGHT)
        val setPoint = ScoreFormatter.pointCall(setPointAfter, Side.RIGHT)
        val setPointBaseline =
            "${ScoreFormatter.number(19)}, ${ScoreFormatter.number(20)}. Set point untuk kanan"

        assertEquals(setPointBaseline.split(" ").size, setPoint.split(" ").size)
    }

    @Test
    fun `banner status dan ucapan sepakat soal set point dan akhir set`() {
        val matchPointBefore = stateAt(19, 18, setsLeft = 1)
        val matchPointAfter = MatchRules.addPoint(matchPointBefore, Side.LEFT)
        assertEquals("Match point", AnnouncementText.statusBanner(matchPointAfter, Side.LEFT))
        assertEquals(
            listOf("dua puluh, delapan belas. Match point untuk kiri"),
            Announcement.forPoint(matchPointBefore, matchPointAfter, Side.LEFT).texts
        )

        val setPointBefore = stateAt(19, 18)
        val setPointAfter = MatchRules.addPoint(setPointBefore, Side.LEFT)
        assertEquals("Set point", AnnouncementText.statusBanner(setPointAfter, Side.LEFT))
        assertEquals(
            listOf("dua puluh, delapan belas. Set point untuk kiri"),
            Announcement.forPoint(setPointBefore, setPointAfter, Side.LEFT).texts
        )

        val setWonBefore = stateAt(20, 18)
        val setWonAfter = MatchRules.addPoint(setWonBefore, Side.LEFT)
        assertEquals("Set pertama untuk kiri", AnnouncementText.statusBanner(setWonAfter, Side.LEFT))
        assertEquals(
            listOf("dua puluh satu, delapan belas", "Set pertama untuk kiri, dua puluh satu delapan belas"),
            Announcement.forPoint(setWonBefore, setWonAfter, Side.LEFT).texts
        )

        val capBefore = stateAt(29, 29)
        val capAfter = MatchRules.addPoint(capBefore, Side.LEFT)
        assertEquals("Set pertama untuk kiri", AnnouncementText.statusBanner(capAfter, Side.LEFT))
        assertEquals(
            listOf("tiga puluh, dua puluh sembilan", "Set pertama untuk kiri, tiga puluh dua puluh sembilan"),
            Announcement.forPoint(capBefore, capAfter, Side.LEFT).texts
        )

        val deuceBefore = stateAt(19, 20)
        val deuceAfter = MatchRules.addPoint(deuceBefore, Side.LEFT)
        assertEquals("Deuce", AnnouncementText.statusBanner(deuceAfter, Side.LEFT))
        assertEquals(
            listOf("dua puluh sama. Deuce"),
            Announcement.forPoint(deuceBefore, deuceAfter, Side.LEFT).texts
        )
    }
}
