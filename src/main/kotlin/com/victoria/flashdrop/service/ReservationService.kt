package com.victoria.flashdrop.service

import com.victoria.flashdrop.controller.request.CreateReservationRequest
import com.victoria.flashdrop.controller.response.ReservationResponse
import com.victoria.flashdrop.mapper.toEntity
import com.victoria.flashdrop.mapper.toResponse
import com.victoria.flashdrop.model.EventRepository
import com.victoria.flashdrop.model.Reservation
import com.victoria.flashdrop.model.ReservationRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ReservationService(
    private val eventRepository: EventRepository,
    private val reservationRepository: ReservationRepository,
) {

    fun getReservation(id: UUID): ReservationResponse {
        val reservation = reservationRepository.findByIdOrNull(id) ?:
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Reservation does not exist"
            )
        return reservation.toResponse()
    }


    @Transactional
    fun reserveTickets(reservationRequest: CreateReservationRequest): ReservationResponse {
        if (reservationRequest.quantity !in 1..5) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported reservation quantity"
            )
        }

        val event = eventRepository.findByIdOrNull(reservationRequest.eventId) ?:
        throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Event does not exist"
        )

        val reservation = reservationRequest.toEntity(event)
        val savedReservation: Reservation?

        if (event.availableTickets >= reservation.quantity) {

            event.availableTickets -= reservation.quantity
            eventRepository.saveAndFlush(event)
            savedReservation = reservationRepository.saveAndFlush(reservation)

        } else {

            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "tickets not available"
            )

        }

        return savedReservation.toResponse()

    }
}