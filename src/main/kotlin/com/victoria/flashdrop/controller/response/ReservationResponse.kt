package com.victoria.flashdrop.controller.response

import java.time.Instant
import java.util.UUID

data class ReservationResponse(
    val id: UUID,
    val eventId: UUID,
    val quantity: Int,
    val status: String,
    val expiresAt: Instant,
    val createdAt: Instant
)