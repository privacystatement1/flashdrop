package com.victoria.flashdrop.integration

import com.victoria.flashdrop.controller.request.CreateReservationRequest
import com.victoria.flashdrop.model.Event
import com.victoria.flashdrop.model.EventRepository
import com.victoria.flashdrop.model.Reservation
import com.victoria.flashdrop.model.ReservationRepository
import com.victoria.flashdrop.model.ReservationStatus
import com.victoria.flashdrop.service.ReservationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReservationExpirationIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var reservationService: ReservationService

    @Autowired
    private lateinit var eventRepository: EventRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    // --------------------------------------------------
    // Basic reservation expiration
    // --------------------------------------------------

    @Test
    fun `should restore tickets when held reservation expires`() {
        // Given
        val event = createEvent(
            capacity = 10,
            availableTickets = 7
        )

        val reservation = createReservation(
            event = event,
            quantity = 3
        )

        // When
        val expired = reservationService.expireReservation(
            reservation.id
        )

        // Then
        assertThat(expired).isTrue()

        assertAvailableTickets(event.id, 10)

        assertReservationStatus(
            reservation.id,
            ReservationStatus.EXPIRED
        )

        assertInventoryInvariant(event.id)
    }

    @Test
    fun `should not expire reservation before its deadline`() {
        // Given
        val event = createEvent(
            capacity = 10,
            availableTickets = 7
        )

        val reservation = createReservation(
            event = event,
            quantity = 3,
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )

        // When
        val expired = reservationService.expireReservation(
            reservation.id
        )

        // Then
        assertThat(expired).isFalse()

        assertAvailableTickets(event.id, 7)

        assertReservationStatus(
            reservation.id,
            ReservationStatus.HELD
        )

        assertInventoryInvariant(event.id)
    }

    @Test
    fun `should not restore tickets for already expired reservation`() {
        // Given
        val event = createEvent(
            capacity = 10,
            availableTickets = 10
        )

        val reservation = createReservation(
            event = event,
            quantity = 3,
            status = ReservationStatus.EXPIRED
        )

        // When
        val expired = reservationService.expireReservation(
            reservation.id
        )

        // Then
        assertThat(expired).isFalse()

        assertAvailableTickets(event.id, 10)

        assertReservationStatus(
            reservation.id,
            ReservationStatus.EXPIRED
        )

        assertInventoryInvariant(event.id)
    }

    @Test
    fun `should not expire confirmed reservation`() {
        // Given
        val event = createEvent(
            capacity = 10,
            availableTickets = 7
        )

        val reservation = createReservation(
            event = event,
            quantity = 3,
            status = ReservationStatus.CONFIRMED
        )

        // When
        val expired = reservationService.expireReservation(
            reservation.id
        )

        // Then
        assertThat(expired).isFalse()

        assertAvailableTickets(event.id, 7)

        assertReservationStatus(
            reservation.id,
            ReservationStatus.CONFIRMED
        )

        assertInventoryInvariant(event.id)
    }

    @Test
    fun `should not restore tickets for cancelled reservation`() {
        // Given
        val event = createEvent(
            capacity = 10,
            availableTickets = 10
        )

        val reservation = createReservation(
            event = event,
            quantity = 3,
            status = ReservationStatus.CANCELLED
        )

        // When
        val expired = reservationService.expireReservation(
            reservation.id
        )

        // Then
        assertThat(expired).isFalse()

        assertAvailableTickets(event.id, 10)

        assertReservationStatus(
            reservation.id,
            ReservationStatus.CANCELLED
        )

        assertInventoryInvariant(event.id)
    }

    @Test
    fun `should return false when reservation does not exist`() {
        // When
        val expired = reservationService.expireReservation(
            UUID.randomUUID()
        )

        // Then
        assertThat(expired).isFalse()
    }

    // --------------------------------------------------
    // Idempotency
    // --------------------------------------------------

    @Test
    fun `should restore inventory only once when expiration is repeated`() {
        // Given
        val event = createEvent(
            capacity = 10,
            availableTickets = 7
        )

        val reservation = createReservation(
            event = event,
            quantity = 3
        )

        // When
        val firstAttempt = reservationService.expireReservation(
            reservation.id
        )

        val secondAttempt = reservationService.expireReservation(
            reservation.id
        )

        val thirdAttempt = reservationService.expireReservation(
            reservation.id
        )

        // Then
        assertThat(firstAttempt).isTrue()
        assertThat(secondAttempt).isFalse()
        assertThat(thirdAttempt).isFalse()

        assertAvailableTickets(event.id, 10)

        assertReservationStatus(
            reservation.id,
            ReservationStatus.EXPIRED
        )

        assertInventoryInvariant(event.id)
    }

    // --------------------------------------------------
    // Concurrent expiration
    // --------------------------------------------------

    @Test
    fun `should expire same reservation only once across 100 concurrent attempts`() {
        // Given
        val event = createEvent(
            capacity = 10,
            availableTickets = 7
        )

        val reservation = createReservation(
            event = event,
            quantity = 3
        )

        // When
        val operations: List<() -> Boolean> =
            List(100) {
                {
                    reservationService.expireReservation(
                        reservation.id
                    )
                }
            }

        val results = runConcurrently(operations)

        // Then
        assertThat(results).hasSize(100)

        assertThat(results.count { it })
            .describedAs("Successful expiration attempts")
            .isEqualTo(1)

        assertThat(results.count { !it })
            .describedAs("Already processed expiration attempts")
            .isEqualTo(99)

        assertAvailableTickets(event.id, 10)

        assertReservationStatus(
            reservation.id,
            ReservationStatus.EXPIRED
        )

        assertReservationCount(event.id, 1)

        assertInventoryInvariant(event.id)
    }

    @Test
    fun `should restore inventory for ten different concurrent expirations`() {
        // Given
        val event = createEvent(
            capacity = 20,
            availableTickets = 0
        )

        val reservations = List(10) {
            createReservation(
                event = event,
                quantity = 2
            )
        }

        // When
        val operations: List<() -> Boolean> =
            reservations.map { reservation ->
                {
                    reservationService.expireReservation(
                        reservation.id
                    )
                }
            }

        val results = runConcurrently(operations)

        // Then
        assertThat(results).hasSize(10)

        assertThat(results.count { it })
            .describedAs("Successful expiration attempts")
            .isEqualTo(10)

        assertAvailableTickets(event.id, 20)

        reservations.forEach { reservation ->
            assertReservationStatus(
                reservation.id,
                ReservationStatus.EXPIRED
            )
        }

        assertReservationCount(event.id, 10)

        assertInventoryInvariant(event.id)
    }

    // --------------------------------------------------
    // Cancellation
    // --------------------------------------------------

    @Test
    fun `should restore tickets when reservation is cancelled`() {
        // Given
        val event = createEvent(
            capacity = 10,
            availableTickets = 7
        )

        val reservation = createReservation(
            event = event,
            quantity = 3,
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )

        // When
        val cancelled = reservationService.cancelReservation(
            reservation.id
        )

        // Then
        assertThat(cancelled).isTrue()

        assertAvailableTickets(event.id, 10)

        assertReservationStatus(
            reservation.id,
            ReservationStatus.CANCELLED
        )

        assertInventoryInvariant(event.id)
    }

    @Test
    fun `should restore inventory only once when cancellation and expiration compete`() {
        // Given
        val event = createEvent(
            capacity = 10,
            availableTickets = 7
        )

        val reservation = createReservation(
            event = event,
            quantity = 3
        )

        val operations = listOf<() -> Boolean>(
            {
                reservationService.expireReservation(
                    reservation.id
                )
            },
            {
                reservationService.cancelReservation(
                    reservation.id
                )
            }
        )

        // When
        val results = runConcurrently(operations)

        // Then
        assertThat(results).hasSize(2)

        assertThat(results.count { it })
            .describedAs("Successful status transitions")
            .isEqualTo(1)

        assertThat(results.count { !it })
            .isEqualTo(1)

        assertAvailableTickets(event.id, 10)

        assertThat(getReservationStatus(reservation.id))
            .isIn(
                ReservationStatus.EXPIRED.name,
                ReservationStatus.CANCELLED.name
            )

        assertReservationCount(event.id, 1)

        assertInventoryInvariant(event.id)
    }

    // --------------------------------------------------
    // Concurrent reservation creation and expiration
    // --------------------------------------------------

    @Test
    fun `should not lose inventory updates when reserving and expiring concurrently`() {
        // Given
        val event = createEvent(
            capacity = 10,
            availableTickets = 7
        )

        val expiredReservation = createReservation(
            event = event,
            quantity = 3
        )

        val reservationOperations: List<() -> Boolean> =
            List(5) {
                {
                    val response = reservationService.createReservation(
                        CreateReservationRequest(
                            eventId = event.id,
                            quantity = 1
                        )
                    )

                    response.quantity == 1 &&
                            response.status == ReservationStatus.HELD.name
                }
            }

        val expirationOperation: () -> Boolean = {
            reservationService.expireReservation(
                expiredReservation.id
            )
        }

        val operations =
            reservationOperations + expirationOperation

        // When
        val results = runConcurrently(operations)

        // Then
        assertThat(results).hasSize(6)

        assertThat(results.count { it })
            .describedAs("Successful operations")
            .isEqualTo(6)

        // Initial availability: 7
        // Five new reservations: -5
        // One expired reservation: +3
        // Final availability: 5

        assertAvailableTickets(event.id, 5)

        assertReservationStatus(
            expiredReservation.id,
            ReservationStatus.EXPIRED
        )

        assertReservationCount(event.id, 6)

        assertThat(
            countReservationsByStatus(
                event.id,
                ReservationStatus.HELD
            )
        ).isEqualTo(5L)

        assertThat(
            countReservationsByStatus(
                event.id,
                ReservationStatus.EXPIRED
            )
        ).isEqualTo(1L)

        assertInventoryInvariant(event.id)
    }

    // --------------------------------------------------
    // Transaction rollback
    // --------------------------------------------------

    @Test
    fun `should roll back inventory and status when expiration transaction fails`() {
        // Given
        val event = createEvent(
            capacity = 10,
            availableTickets = 7
        )

        val reservation = createReservation(
            event = event,
            quantity = 3
        )

        val transactionTemplate =
            TransactionTemplate(transactionManager)

        // When
        val exception = assertThrows<IllegalStateException> {

            transactionTemplate.executeWithoutResult {

                val expired =
                    reservationService.expireReservation(
                        reservation.id
                    )

                assertThat(expired).isTrue()

                // Force Hibernate to send both updates to PostgreSQL.
                reservationRepository.flush()

                // Confirm both updates exist within this transaction.
                assertAvailableTickets(event.id, 10)

                assertReservationStatus(
                    reservation.id,
                    ReservationStatus.EXPIRED
                )

                // Simulate a failure before transaction commit.
                throw IllegalStateException(
                    "Simulated transaction failure"
                )
            }
        }

        // Then
        assertThat(exception.message)
            .isEqualTo("Simulated transaction failure")

        // Query the database after rollback.
        assertAvailableTickets(event.id, 7)

        assertReservationStatus(
            reservation.id,
            ReservationStatus.HELD
        )

        assertInventoryInvariant(event.id)

        // Verify that the operation can safely be retried.
        assertThat(
            reservationService.expireReservation(reservation.id)
        ).isTrue()

        assertAvailableTickets(event.id, 10)

        assertReservationStatus(
            reservation.id,
            ReservationStatus.EXPIRED
        )

        assertInventoryInvariant(event.id)
    }

    // --------------------------------------------------
    // Concurrency helper
    // --------------------------------------------------

    private fun <T> runConcurrently(
        operations: List<() -> T>
    ): List<T> {

        require(operations.isNotEmpty())

        val executor = Executors.newFixedThreadPool(
            operations.size
        )

        val readyLatch = CountDownLatch(operations.size)
        val startLatch = CountDownLatch(1)

        try {
            val futures = operations.map { operation ->

                executor.submit<T> {

                    readyLatch.countDown()

                    check(
                        startLatch.await(30, TimeUnit.SECONDS)
                    ) {
                        "Timed out waiting for start signal"
                    }

                    operation()
                }
            }

            assertThat(
                readyLatch.await(30, TimeUnit.SECONDS)
            )
                .describedAs("All workers should be ready")
                .isTrue()

            startLatch.countDown()

            return futures.map { future ->
                future.get(60, TimeUnit.SECONDS)
            }

        } finally {
            startLatch.countDown()
            executor.shutdownNow()
        }
    }

    // --------------------------------------------------
    // Test fixture helpers
    // --------------------------------------------------

    private fun createEvent(
        capacity: Int,
        availableTickets: Int
    ): Event {

        return eventRepository.saveAndFlush(
            Event(
                title = "Expiration Test ${UUID.randomUUID()}",
                venue = "Test Venue",
                city = "Boston",
                startsAt = Instant.now().plus(7, ChronoUnit.DAYS),
                capacity = capacity,
                availableTickets = availableTickets
            )
        )
    }

    private fun createReservation(
        event: Event,
        quantity: Int,
        status: ReservationStatus = ReservationStatus.HELD,
        expiresAt: Instant = Instant.now()
            .minus(5, ChronoUnit.MINUTES)
    ): Reservation {

        return reservationRepository.saveAndFlush(
            Reservation(
                event = event,
                quantity = quantity,
                status = status,
                expiresAt = expiresAt
            )
        )
    }

    // --------------------------------------------------
    // Database assertion helpers
    // --------------------------------------------------

    private fun assertAvailableTickets(
        eventId: UUID,
        expected: Int
    ) {

        val availableTickets = jdbcTemplate.queryForObject(
            """
            SELECT available_tickets
            FROM events
            WHERE id = ?
            """.trimIndent(),
            Int::class.javaObjectType,
            eventId
        )

        assertThat(availableTickets)
            .describedAs("Available tickets for event $eventId")
            .isEqualTo(expected)
    }

    private fun getReservationStatus(
        reservationId: UUID
    ): String {

        return requireNotNull(
            jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM reservations
                WHERE id = ?
                """.trimIndent(),
                String::class.java,
                reservationId
            )
        )
    }

    private fun assertReservationStatus(
        reservationId: UUID,
        expected: ReservationStatus
    ) {

        assertThat(getReservationStatus(reservationId))
            .describedAs("Reservation status for $reservationId")
            .isEqualTo(expected.name)
    }

    private fun assertReservationCount(
        eventId: UUID,
        expected: Long
    ) {

        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM reservations
            WHERE event_id = ?
            """.trimIndent(),
            Long::class.javaObjectType,
            eventId
        )

        assertThat(count)
            .describedAs("Persisted reservation count")
            .isEqualTo(expected)
    }

    private fun countReservationsByStatus(
        eventId: UUID,
        status: ReservationStatus
    ): Long {

        return requireNotNull(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM reservations
                WHERE event_id = ?
                AND status = ?
                """.trimIndent(),
                Long::class.javaObjectType,
                eventId,
                status.name
            )
        )
    }

    private fun assertInventoryInvariant(eventId: UUID) {

        val inventory = jdbcTemplate.queryForMap(
            """
            SELECT
                e.capacity,
                e.available_tickets,
                COALESCE(
                    (
                        SELECT SUM(r.quantity)
                        FROM reservations r
                        WHERE r.event_id = e.id
                        AND r.status IN ('HELD', 'CONFIRMED')
                    ),
                    0
                ) AS allocated_tickets
            FROM events e
            WHERE e.id = ?
            """.trimIndent(),
            eventId
        )

        val capacity =
            (inventory.getValue("capacity") as Number).toLong()

        val available =
            (inventory.getValue("available_tickets") as Number).toLong()

        val allocated =
            (inventory.getValue("allocated_tickets") as Number).toLong()

        assertThat(available)
            .describedAs("Available tickets must not be negative")
            .isGreaterThanOrEqualTo(0L)

        assertThat(available)
            .describedAs("Available tickets must not exceed capacity")
            .isLessThanOrEqualTo(capacity)

        assertThat(available + allocated)
            .describedAs(
                "Inventory invariant: capacity=$capacity, " +
                        "available=$available, allocated=$allocated"
            )
            .isEqualTo(capacity)
    }
}