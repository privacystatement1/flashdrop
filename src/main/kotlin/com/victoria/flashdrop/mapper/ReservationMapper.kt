package com.victoria.flashdrop.mapper

import com.victoria.flashdrop.controller.request.CreateReservationRequest
import com.victoria.flashdrop.controller.response.ReservationResponse
import com.victoria.flashdrop.model.Event
import com.victoria.flashdrop.model.Reservation
import com.victoria.flashdrop.model.ReservationStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

fun CreateReservationRequest.toEntity(event: Event): Reservation =
    Reservation(
        event = event,
        quantity = quantity,
        status = ReservationStatus.HELD,
        expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES)
    )

fun Reservation.toResponse(): ReservationResponse =
    ReservationResponse(
        id = id,
        eventId = event.id,
        quantity = quantity,
        status = status.name,
        expiresAt = expiresAt,
        createdAt = checkNotNull(createdAt) {
            "Reservation createdAt has not been loaded"
        }
    )