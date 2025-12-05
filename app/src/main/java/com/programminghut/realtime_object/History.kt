package com.programminghut.realtime_object

import com.google.firebase.Timestamp

data class History(
    val imageUrl: String = "",
    val label: String = "",
    val score: Float = 0f,
    val timestamp: Timestamp = Timestamp.now()
)
    