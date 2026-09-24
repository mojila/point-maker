package com.pointmaker.app.wear

data class WearAnnouncement(
    val texts: List<String>,
    val feedback: WearFeedback,
    val playCue: Boolean
)
