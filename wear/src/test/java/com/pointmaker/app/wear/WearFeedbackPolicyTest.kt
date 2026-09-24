package com.pointmaker.app.wear

import com.pointmaker.app.domain.Announcement
import org.junit.Assert.assertEquals
import org.junit.Test

class WearFeedbackPolicyTest {

    @Test
    fun `poin biasa memakai tick`() {
        assertEquals(WearFeedback.POINT, WearFeedbackPolicy.forPriority(Announcement.Priority.SCORE))
    }

    @Test
    fun `set point dan match point memakai emphasis`() {
        assertEquals(WearFeedback.EMPHASIS, WearFeedbackPolicy.forPriority(Announcement.Priority.SET_POINT))
        assertEquals(WearFeedback.EMPHASIS, WearFeedbackPolicy.forPriority(Announcement.Priority.MATCH_POINT))
    }

    @Test
    fun `set dan pertandingan selesai memakai emphasis`() {
        assertEquals(WearFeedback.EMPHASIS, WearFeedbackPolicy.forPriority(Announcement.Priority.SET_FINISHED))
        assertEquals(WearFeedback.EMPHASIS, WearFeedbackPolicy.forPriority(Announcement.Priority.MATCH_FINISHED))
    }
}
