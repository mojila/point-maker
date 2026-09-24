package com.pointmaker.app.domain

object ScoreFormatter {

    private val ZERO_TO_ELEVEN = listOf(
        "nol", "satu", "dua", "tiga", "empat", "lima", "enam",
        "tujuh", "delapan", "sembilan", "sepuluh", "sebelas"
    )

    private val TENS = mapOf(2 to "dua puluh", 3 to "tiga puluh")

    fun number(value: Int): String {
        val clamped = value.coerceIn(0, MatchRules.HARD_CAP)
        if (clamped < ZERO_TO_ELEVEN.size) return ZERO_TO_ELEVEN[clamped]
        if (clamped < 20) return ZERO_TO_ELEVEN[clamped - 10] + " belas"
        val tens = TENS[clamped / 10] ?: error("tidak ada puluhan untuk $clamped")
        val unit = clamped % 10
        return if (unit == 0) tens else "$tens ${ZERO_TO_ELEVEN[unit]}"
    }

    fun sideName(side: Side): String = if (side == Side.LEFT) "kiri" else "kanan"

    fun scorePair(left: Int, right: Int): String = "${number(left)}, ${number(right)}"

    fun scorePairSpaced(left: Int, right: Int): String = "${number(left)} ${number(right)}"

    fun setOrdinal(setNumber: Int): String = when (setNumber) {
        1 -> "pertama"
        2 -> "kedua"
        3 -> "ketiga"
        else -> error("set hanya 1..3: $setNumber")
    }

    fun pointCall(state: MatchState): String = pointCall(state, scoringSideOf(state))

    fun pointCall(state: MatchState, scoringSide: Side): String {
        if (MatchRules.isDeuce(state)) return deuce()
        val score = scorePair(state.leftPoints, state.rightPoints)
        if (MatchRules.isMatchPoint(state, scoringSide)) {
            return "$score. Match point untuk ${sideName(scoringSide)}"
        }
        if (MatchRules.isSetPoint(state, scoringSide)) {
            return if (state.pointsOf(scoringSide) >= MatchRules.HARD_CAP) limitedThirty(scoringSide)
            else "$score. Set point untuk ${sideName(scoringSide)}"
        }
        return score
    }

    /**
     * Menyimpulkan sisi pencetak poin dari state pasca-poin.
     *
     * Hanya dapat dipakai pada state hasil [MatchRules.addPoint] yang tidak seri. Untuk state
     * yang tidak dapat disimpulkan (skor seri seperti 20-20, atau reli terakhir yang tidak dicatat)
     * pemanggil sebaiknya memakai [pointCall] dua argumen. Fungsi ini tidak melempar exception agar
     * pemanggilan yang tidak sengaja tidak mematikan suara di tengah reli.
     */
    fun scoringSideOf(state: MatchState): Side = when {
        state.leftPoints > state.rightPoints -> Side.LEFT
        state.rightPoints > state.leftPoints -> Side.RIGHT
        else -> state.servingSide
    }

    fun deuce(): String = "dua puluh sama. Deuce"

    fun limitedThirty(setPointSide: Side): String =
        "tiga puluh dua puluh sembilan. Set point untuk ${sideName(setPointSide)}"

    fun setFinished(winner: Side, setNumber: Int, left: Int, right: Int): String =
        "Set ${setOrdinal(setNumber)} untuk ${sideName(winner)}, ${scorePairSpaced(left, right)}"

    fun nextSetStart(setNumber: Int): String = "Set ${setOrdinal(setNumber)}, mulai"

    fun matchFinished(winner: Side, setsWon: Int, setsLost: Int): String =
        "Pertandingan selesai. Pemenang: ${sideName(winner)}, ${number(setsWon)} set ${number(setsLost)}"

    fun announcePoint(before: MatchState, after: MatchState, scoringSide: Side): String =
        scoringCall(before, scoringSide)

    private fun scoringCall(before: MatchState, scoringSide: Side): String {
        val scoring = MatchRules.scoredOnly(before, scoringSide)
        if (MatchRules.isDeuce(scoring)) return deuce()
        val score = scorePair(scoring.leftPoints, scoring.rightPoints)
        if (MatchRules.isMatchPointBeforeSetClosure(before, scoringSide)) {
            return "$score. Match point untuk ${sideName(scoringSide)}"
        }
        if (MatchRules.isSetPointBeforeSetClosure(before, scoringSide)) {
            return if (scoring.pointsOf(scoringSide) >= MatchRules.HARD_CAP) limitedThirty(scoringSide)
            else "$score. Set point untuk ${sideName(scoringSide)}"
        }
        return score
    }

    fun announcements(before: MatchState, after: MatchState, scoringSide: Side): List<String> {
        if (after === before) return emptyList()
        val winner = MatchRules.setWinner(after)
        if (winner == null) return listOf(announcePoint(before, after, scoringSide))
        return buildList {
            add(scoringCall(before, scoringSide))
            add(setFinished(winner, before.currentSet, after.leftPoints, after.rightPoints))
            MatchRules.matchWinner(after)?.let { matchWinner ->
                add(
                    matchFinished(
                        matchWinner,
                        after.setsWonOf(matchWinner),
                        after.setsWonOf(matchWinner.opponent())
                    )
                )
            }
        }
    }
}
