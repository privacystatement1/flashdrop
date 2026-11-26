package com.victoria.flashdrop.integration

import com.victoria.flashdrop.controller.request.CreateReservationRequest
import com.victoria.flashdrop.model.Event
import com.victoria.flashdrop.model.EventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.EntityExchangeResult
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReservationConcurrencyTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var eventRepository: EventRepository

    @Autowired
    private lateinit var restTestClient: RestTestClient

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `only one of 100 concurrent requests succeeds with one available ticket`() {

        val event = createEvent(availableTickets = 1)

        val responses = reserveConcurrently(
            eventId = event.id,
            requestCount = 100
        )

        assertReservationResults(
            eventId = event.id,
            responses = responses,
            expectedSuccesses = 1,
            expectedConflicts = 99
        )
    }

    @Test
    fun `only ten of 20 concurrent requests succeed with ten available tickets`() {

        val event = createEvent(availableTickets = 10)

        val responses = reserveConcurrently(
            eventId = event.id,
            requestCount = 20
        )

        assertReservationResults(
            eventId = event.id,
            responses = responses,
            expectedSuccesses = 10,
            expectedConflicts = 10
        )
    }

    private fun createEvent(availableTickets: Int): Event {

        return eventRepository.saveAndFlush(
            Event(
                title = "Concurrency Test ${UUID.randomUUID()}",
                venue = "Test Venue",
                city = "Test City",
                startsAt = Instant.now().plus(7, ChronoUnit.DAYS),
                capacity = 10000,
                availableTickets = availableTickets
            )
        )
    }

    private fun reserveConcurrently(
        eventId: UUID,
        requestCount: Int
    ): List<EntityExchangeResult<ByteArray>> {

        val executor = Executors.newFixedThreadPool(requestCount)

        val readyLatch = CountDownLatch(requestCount)
        val startLatch = CountDownLatch(1)

        try {

            val futures = (1..requestCount).map {

                executor.submit<EntityExchangeResult<ByteArray>> {

                    val request = CreateReservationRequest(
                        eventId = eventId,
                        quantity = 1
                    )

                    readyLatch.countDown()

                    check(startLatch.await(30, TimeUnit.SECONDS)) {
                        "Timed out waiting for start signal"
                    }

                    restTestClient.post()
                        .uri("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .exchange()
                        .expectBody()
                        .returnResult()
                }
            }

            assertThat(readyLatch.await(30, TimeUnit.SECONDS))
                .describedAs("All workers should be ready")
                .isTrue()

            startLatch.countDown()

            return futures.map {
                it.get(60, TimeUnit.SECONDS)
            }

        } finally {
            startLatch.countDown()
            executor.shutdownNow()
        }
    }

    private fun assertReservationResults(
        eventId: UUID,
        responses: List<EntityExchangeResult<ByteArray>>,
        expectedSuccesses: Int,
        expectedConflicts: Int
    ) {

        val successes = responses.count {
            it.status == HttpStatus.CREATED
        }

        val conflicts = responses.count {
            it.status == HttpStatus.CONFLICT
        }

        val unexpectedResponses = responses.filter {
            it.status != HttpStatus.CREATED &&
                    it.status != HttpStatus.CONFLICT
        }

        val persistedReservations = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM reservations
            WHERE event_id = ?
            """.trimIndent(),
            Long::class.javaObjectType,
            eventId
        )

        val availableTickets = jdbcTemplate.queryForObject(
            """
            SELECT available_tickets
            FROM events
            WHERE id = ?
            """.trimIndent(),
            Int::class.javaObjectType,
            eventId
        )

        val reservedQuantity = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(quantity), 0)
            FROM reservations
            WHERE event_id = ?
            """.trimIndent(),
            Long::class.javaObjectType,
            eventId
        )

        println(
            """
            Reservation concurrency test:
            Total requests: ${responses.size}
            Successful requests: $successes
            Conflicting requests: $conflicts
            Unexpected responses: ${unexpectedResponses.size}
            Persisted reservations: $persistedReservations
            Reserved quantity: $reservedQuantity
            Available tickets: $availableTickets
            """.trimIndent()
        )

        assertThat(responses)
            .describedAs("Total completed requests")
            .hasSize(expectedSuccesses + expectedConflicts)

        assertThat(unexpectedResponses)
            .describedAs("Unexpected HTTP responses")
            .isEmpty()

        assertThat(successes)
            .describedAs("Successful reservations")
            .isEqualTo(expectedSuccesses)

        assertThat(conflicts)
            .describedAs("Conflicting reservations")
            .isEqualTo(expectedConflicts)

        assertThat(persistedReservations)
            .describedAs("Persisted reservation rows")
            .isEqualTo(expectedSuccesses.toLong())

        assertThat(reservedQuantity)
            .describedAs("Total reserved ticket quantity")
            .isEqualTo(expectedSuccesses.toLong())

        assertThat(availableTickets)
            .describedAs("Remaining available tickets")
            .isEqualTo(0)
    }
}