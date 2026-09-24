package com.pointmaker.app.domain

data class Announcement(
    val texts: List<String>,
    val priority: Priority
) {
    enum class Priority {
        SCORE,
        SET_POINT,
        MATCH_POINT,
        SET_FINISHED,
        MATCH_FINISHED
    }

    companion object {
        val NONE = Announcement(emptyList(), Priority.SCORE)

        fun forPoint(before: MatchState, after: MatchState, scoringSide: Side): Announcement {
            if (after === before) return NONE
            val texts = ScoreFormatter.announcements(before, after, scoringSide)
            if (texts.isEmpty()) return NONE
            return Announcement(texts, priorityOf(after, scoringSide))
        }

        private fun priorityOf(state: MatchState, scoringSide: Side): Priority = when {
            MatchRules.isGameFinished(state) -> Priority.MATCH_FINISHED
            MatchRules.isSetFinished(state) -> Priority.SET_FINISHED
            MatchRules.isMatchPoint(state, scoringSide) -> Priority.MATCH_POINT
            MatchRules.isSetPoint(state, scoringSide) -> Priority.SET_POINT
            else -> Priority.SCORE
        }
    }
}
