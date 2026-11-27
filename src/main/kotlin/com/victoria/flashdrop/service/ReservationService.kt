package com.victoria.flashdrop.service

import com.victoria.flashdrop.controller.request.CreateReservationRequest
import com.victoria.flashdrop.controller.response.ReservationResponse
import com.victoria.flashdrop.mapper.toEntity
import com.victoria.flashdrop.mapper.toResponse
import com.victoria.flashdrop.model.EventRepository
import com.victoria.flashdrop.model.Reservation
import com.victoria.flashdrop.model.ReservationRepository
import com.victoria.flashdrop.model.ReservationStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
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
    fun createReservation(reservationRequest: CreateReservationRequest): ReservationResponse {
        if (reservationRequest.quantity !in 1..5) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported reservation quantity"
            )
        }

        val event = eventRepository.findByIdForUpdate(reservationRequest.eventId) ?:
        throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Event does not exist"
        )

        val reservation = reservationRequest.toEntity(event)
        val savedReservation: Reservation?

        if (event.availableTickets >= reservation.quantity) {

            event.availableTickets -= reservation.quantity
            savedReservation = reservationRepository.saveAndFlush(reservation)

        } else {

            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "tickets not available"
            )

        }

        return savedReservation.toResponse()

    }

    @Transactional
    fun expireReservation(reservationId: UUID): Boolean {

        val reservation = reservationRepository
            .findByIdForUpdate(reservationId)
            ?: return false

        // Only held reservations can expire.
        if (reservation.status != ReservationStatus.HELD) {
            return false
        }

        // Do not expire reservations before their expiration time.
        if (reservation.expiresAt.isAfter(Instant.now())) {
            return false
        }

        val event = eventRepository.findByIdForUpdate(
            reservation.event.id
        ) ?: error("Event associated with reservation does not exist")

        event.availableTickets += reservation.quantity

        reservation.status = ReservationStatus.EXPIRED

        return true
    }

    @Transactional
    fun cancelReservation(reservationId: UUID): Boolean {

        val reservation = reservationRepository
            .findByIdForUpdate(reservationId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Reservation does not exist"
            )

        if (reservation.status != ReservationStatus.HELD) {
            return false
        }

        val event = eventRepository.findByIdForUpdate(
            reservation.event.id
        ) ?: error("Event associated with reservation does not exist")

        event.availableTickets += reservation.quantity

        reservation.status = ReservationStatus.CANCELLED

        return true
    }

    @Transactional
    fun confirmReservation(reservationId: UUID): Boolean {

        val reservation = reservationRepository
            .findByIdForUpdate(reservationId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Reservation does not exist"
            )

        if (reservation.status != ReservationStatus.HELD) {
            return false
        }

        reservation.status = ReservationStatus.CONFIRMED

        return true
    }
}