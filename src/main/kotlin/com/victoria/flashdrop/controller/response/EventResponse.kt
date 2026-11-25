package com.victoria.flashdrop.controller.response

import java.util.UUID
import java.time.Instant

data class EventResponse(
    val id: UUID,
    val title: String,
    val venue: String,
    val city: String,
    val startsAt: Instant,
    val capacity: Int,
    val availableTickets: Int,
    val createdAt: Instant
)