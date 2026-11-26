package com.victoria.flashdrop.controller.request

import jakarta.validation.constraints.Positive
import java.util.UUID

data class CreateReservationRequest(
    val eventId: UUID,

    @field:Positive
    val quantity: Int
)