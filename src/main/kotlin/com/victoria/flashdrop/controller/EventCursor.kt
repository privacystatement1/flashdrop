package com.victoria.flashdrop.controller

import java.time.Instant
import java.util.UUID

data class EventCursor(
    val startsAt: Instant,
    val id: UUID
)