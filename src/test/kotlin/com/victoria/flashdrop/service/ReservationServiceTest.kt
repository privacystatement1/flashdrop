package com.victoria.flashdrop.service

import com.victoria.flashdrop.controller.request.CreateReservationRequest
import com.victoria.flashdrop.model.Event
import com.victoria.flashdrop.model.EventRepository
import com.victoria.flashdrop.model.Reservation
import com.victoria.flashdrop.model.ReservationRepository
import com.victoria.flashdrop.model.ReservationStatus
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ReservationServiceTest {

    private lateinit var eventRepository: EventRepository
    private lateinit var reservationRepository: ReservationRepository
    private lateinit var reservationService: ReservationService

    @BeforeEach
    fun setUp() {
        eventRepository = mockk()
        reservationRepository = mockk()

        reservationService = ReservationService(
            eventRepository,
            reservationRepository
        )
    }

    // Successful reservations

    @Test
    fun `should successfully reserve one ticket`() {
        // Given
        val event = createEvent(availableTickets = 10)

        val request = CreateReservationRequest(
            eventId = event.id,
            quantity = 1
        )

        every {
            eventRepository.findByIdForUpdate(event.id)
        } returns event

        mockReservationSave()

        // When
        val response = reservationService.reserveTickets(request)

        // Then
        assertThat(response.eventId).isEqualTo(event.id)
        assertThat(response.quantity).isEqualTo(1)
        assertThat(response.status).isEqualTo("HELD")
        assertThat(event.availableTickets).isEqualTo(9)

        verify(exactly = 1) {
            eventRepository.findByIdForUpdate(event.id)
        }

        verify(exactly = 1) {
            reservationRepository.saveAndFlush(
                match {
                    it.event.id == event.id &&
                            it.quantity == 1 &&
                            it.status == ReservationStatus.HELD
                }
            )
        }
    }

    @Test
    fun `should successfully reserve multiple tickets`() {
        // Given
        val event = createEvent(availableTickets = 10)

        val request = CreateReservationRequest(
            eventId = event.id,
            quantity = 3
        )

        every {
            eventRepository.findByIdForUpdate(event.id)
        } returns event

        mockReservationSave()

        // When
        val response = reservationService.reserveTickets(request)

        // Then
        assertThat(response.quantity).isEqualTo(3)
        assertThat(event.availableTickets).isEqualTo(7)

        verify(exactly = 1) {
            reservationRepository.saveAndFlush(any())
        }
    }

    @Test
    fun `should successfully reserve all remaining tickets`() {
        // Given
        val event = createEvent(availableTickets = 3)

        val request = CreateReservationRequest(
            eventId = event.id,
            quantity = 3
        )

        every {
            eventRepository.findByIdForUpdate(event.id)
        } returns event

        mockReservationSave()

        // When
        val response = reservationService.reserveTickets(request)

        // Then
        assertThat(response.quantity).isEqualTo(3)
        assertThat(event.availableTickets).isZero()

        verify(exactly = 1) {
            reservationRepository.saveAndFlush(any())
        }
    }

    @Test
    fun `should allow the maximum reservation quantity of five`() {
        // Given
        val event = createEvent(availableTickets = 10)

        val request = CreateReservationRequest(
            eventId = event.id,
            quantity = 5
        )

        every {
            eventRepository.findByIdForUpdate(event.id)
        } returns event

        mockReservationSave()

        // When
        val response = reservationService.reserveTickets(request)

        // Then
        assertThat(response.quantity).isEqualTo(5)
        assertThat(event.availableTickets).isEqualTo(5)

        verify(exactly = 1) {
            reservationRepository.saveAndFlush(any())
        }
    }

    // Invalid quantities

    @ParameterizedTest
    @ValueSource(ints = [-10, -1, 0, 6, 10, 100])
    fun `should reject unsupported reservation quantities`(
        quantity: Int
    ) {
        // Given
        val request = CreateReservationRequest(
            eventId = UUID.randomUUID(),
            quantity = quantity
        )

        // When
        val exception = assertThrows<ResponseStatusException> {
            reservationService.reserveTickets(request)
        }

        // Then
        assertThat(exception.statusCode)
            .isEqualTo(HttpStatus.BAD_REQUEST)

        assertThat(exception.reason)
            .isEqualTo("Unsupported reservation quantity")

        verify {
            eventRepository wasNot Called
            reservationRepository wasNot Called
        }
    }

    // Event not found

    @Test
    fun `should throw not found when event does not exist`() {
        // Given
        val eventId = UUID.randomUUID()

        val request = CreateReservationRequest(
            eventId = eventId,
            quantity = 1
        )

        every {
            eventRepository.findByIdForUpdate(eventId)
        } returns null

        // When
        val exception = assertThrows<ResponseStatusException> {
            reservationService.reserveTickets(request)
        }

        // Then
        assertThat(exception.statusCode)
            .isEqualTo(HttpStatus.NOT_FOUND)

        assertThat(exception.reason)
            .isEqualTo("Event does not exist")

        verify(exactly = 1) {
            eventRepository.findByIdForUpdate(eventId)
        }

        verify {
            reservationRepository wasNot Called
        }
    }

    // Insufficient tickets

    @Test
    fun `should throw conflict when requested quantity exceeds available tickets`() {
        // Given
        val event = createEvent(availableTickets = 2)

        val request = CreateReservationRequest(
            eventId = event.id,
            quantity = 3
        )

        every {
            eventRepository.findByIdForUpdate(event.id)
        } returns event

        // When
        val exception = assertThrows<ResponseStatusException> {
            reservationService.reserveTickets(request)
        }

        // Then
        assertThat(exception.statusCode)
            .isEqualTo(HttpStatus.CONFLICT)

        assertThat(exception.reason)
            .isEqualTo("tickets not available")

        assertThat(event.availableTickets).isEqualTo(2)

        verify {
            reservationRepository wasNot Called
        }

        verify(exactly = 0) {
            eventRepository.saveAndFlush(any())
        }
    }

    @Test
    fun `should throw conflict when event is sold out`() {
        // Given
        val event = createEvent(availableTickets = 0)

        val request = CreateReservationRequest(
            eventId = event.id,
            quantity = 1
        )

        every {
            eventRepository.findByIdForUpdate(event.id)
        } returns event

        // When
        val exception = assertThrows<ResponseStatusException> {
            reservationService.reserveTickets(request)
        }

        // Then
        assertThat(exception.statusCode)
            .isEqualTo(HttpStatus.CONFLICT)

        assertThat(exception.reason)
            .isEqualTo("tickets not available")

        assertThat(event.availableTickets).isZero()

        verify {
            reservationRepository wasNot Called
        }
    }

    // Retrieving reservations

    @Test
    fun `should return reservation when it exists`() {
        // Given
        val event = createEvent(availableTickets = 9)
        val reservation = createReservation(event)

        every {
            reservationRepository.findById(reservation.id)
        } returns java.util.Optional.of(reservation)

        // When
        val response = reservationService.getReservation(
            reservation.id
        )

        // Then
        assertThat(response.id).isEqualTo(reservation.id)
        assertThat(response.eventId).isEqualTo(event.id)
        assertThat(response.quantity).isEqualTo(reservation.quantity)
        assertThat(response.status).isEqualTo("HELD")
        assertThat(response.createdAt).isEqualTo(reservation.createdAt)
        assertThat(response.expiresAt).isEqualTo(reservation.expiresAt)

        verify(exactly = 1) {
            reservationRepository.findById(reservation.id)
        }
    }

    @Test
    fun `should throw not found when reservation does not exist`() {
        // Given
        val reservationId = UUID.randomUUID()

        every {
            reservationRepository.findById(reservationId)
        } returns java.util.Optional.empty()

        // When
        val exception = assertThrows<ResponseStatusException> {
            reservationService.getReservation(reservationId)
        }

        // Then
        assertThat(exception.statusCode)
            .isEqualTo(HttpStatus.NOT_FOUND)

        assertThat(exception.reason)
            .isEqualTo("Reservation does not exist")

        verify(exactly = 1) {
            reservationRepository.findById(reservationId)
        }
    }

    // Test helpers

    private fun createEvent(
        availableTickets: Int = 10
    ): Event {
        return Event(
            title = "Test Concert",
            venue = "Test Arena",
            city = "Boston",
            startsAt = Instant.now().plus(7, ChronoUnit.DAYS),
            capacity = 10,
            availableTickets = availableTickets
        )
    }

    private fun createReservation(
        event: Event,
        quantity: Int = 1
    ): Reservation {

        val reservation = Reservation(
            event = event,
            quantity = quantity,
            status = ReservationStatus.HELD,
            expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)
        )

        ReflectionTestUtils.setField(
            reservation,
            "createdAt",
            Instant.now()
        )

        return reservation
    }

    private fun mockReservationSave() {
        every {
            reservationRepository.saveAndFlush(any())
        } answers {
            firstArg<Reservation>().also {
                ReflectionTestUtils.setField(
                    it,
                    "createdAt",
                    Instant.now()
                )
            }
        }
    }
}