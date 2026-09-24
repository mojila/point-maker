package com.pointmaker.app.wear

import com.pointmaker.app.domain.Announcement

enum class WearFeedback {
    POINT,
    EMPHASIS,
    UNDO,
    NONE
}

object WearFeedbackPolicy {

    fun forPriority(priority: Announcement.Priority): WearFeedback = when (priority) {
        Announcement.Priority.SCORE -> WearFeedback.POINT
        Announcement.Priority.SET_POINT,
        Announcement.Priority.MATCH_POINT,
        Announcement.Priority.SET_FINISHED,
        Announcement.Priority.MATCH_FINISHED -> WearFeedback.EMPHASIS
    }
}
