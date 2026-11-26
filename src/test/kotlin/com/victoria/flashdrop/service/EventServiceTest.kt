package com.victoria.flashdrop.service

import com.victoria.flashdrop.controller.request.CreateEventRequest
import com.victoria.flashdrop.controller.request.EventCursor
import com.victoria.flashdrop.model.Event
import com.victoria.flashdrop.model.EventRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaSpecificationExecutor.SpecificationFluentQuery
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.function.Function

class EventServiceTest {

    private lateinit var eventRepository: EventRepository
    private lateinit var eventService: EventService

    private val createdAt = Instant.parse("2026-01-01T10:00:00Z")
    private val fromStartsAt = Instant.parse("2027-01-01T00:00:00Z")
    private val toStartsAt = Instant.parse("2027-02-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        eventRepository = mockk()
        eventService = EventService(eventRepository)
    }

    // --------------------------------------------------
    // Offset-based pagination
    // --------------------------------------------------

    @Test
    fun `should return events with correct pagination metadata`() {
        // Given
        val events = listOf(
            createTestEvent(title = "Concert A"),
            createTestEvent(title = "Concert B")
        )

        val pageable = PageRequest.of(
            0,
            2,
            Sort.by("startsAt").ascending()
        )

        val page = PageImpl(events, pageable, 5L)

        every {
            eventRepository.findAll(
                any<Specification<Event>>(),
                pageable
            )
        } returns page

        // When
        val response = eventService.getEvents(
            city = null,
            venue = null,
            name = null,
            availableOnly = null,
            fromStartsAt = null,
            toStartsAt = null,
            pageable = pageable
        )

        // Then
        assertThat(response.content).hasSize(2)

        assertThat(response.content.map { it.title })
            .containsExactly("Concert A", "Concert B")

        assertThat(response.content.map { it.id })
            .containsExactlyElementsOf(events.map { it.id })

        assertThat(response.page).isEqualTo(0)
        assertThat(response.size).isEqualTo(2)
        assertThat(response.totalElements).isEqualTo(5L)
        assertThat(response.totalPages).isEqualTo(3)

        verify(exactly = 1) {
            eventRepository.findAll(
                any<Specification<Event>>(),
                pageable
            )
        }
    }

    @Test
    fun `should return empty page when no events exist`() {
        // Given
        val pageable = PageRequest.of(0, 20)

        every {
            eventRepository.findAll(
                any<Specification<Event>>(),
                pageable
            )
        } returns PageImpl(emptyList<Event>(), pageable, 0L)

        // When
        val response = eventService.getEvents(
            city = null,
            venue = null,
            name = null,
            availableOnly = null,
            fromStartsAt = null,
            toStartsAt = null,
            pageable = pageable
        )

        // Then
        assertThat(response.content).isEmpty()
        assertThat(response.page).isZero()
        assertThat(response.size).isEqualTo(20)
        assertThat(response.totalElements).isZero()
        assertThat(response.totalPages).isZero()
    }

    @Test
    fun `should pass requested pagination and sorting to repository`() {
        // Given
        val pageable = PageRequest.of(
            2,
            10,
            Sort.by("startsAt").descending()
        )

        every {
            eventRepository.findAll(
                any<Specification<Event>>(),
                pageable
            )
        } returns PageImpl(emptyList<Event>(), pageable, 0L)

        // When
        val response = eventService.getEvents(
            city = null,
            venue = null,
            name = null,
            availableOnly = null,
            fromStartsAt = null,
            toStartsAt = null,
            pageable = pageable
        )

        // Then
        assertThat(response.page).isEqualTo(2)
        assertThat(response.size).isEqualTo(10)

        verify(exactly = 1) {
            eventRepository.findAll(
                any<Specification<Event>>(),
                pageable
            )
        }
    }

    @Test
    fun `should map all event fields to response`() {
        // Given
        val event = createTestEvent(
            title = "Rock Concert",
            capacity = 100,
            availableTickets = 75
        )

        val pageable = PageRequest.of(0, 20)

        every {
            eventRepository.findAll(
                any<Specification<Event>>(),
                pageable
            )
        } returns PageImpl(listOf(event), pageable, 1L)

        // When
        val response = eventService.getEvents(
            city = null,
            venue = null,
            name = null,
            availableOnly = null,
            fromStartsAt = null,
            toStartsAt = null,
            pageable = pageable
        )

        // Then
        val result = response.content.single()

        assertThat(result.id).isEqualTo(event.id)
        assertThat(result.title).isEqualTo(event.title)
        assertThat(result.venue).isEqualTo(event.venue)
        assertThat(result.city).isEqualTo(event.city)
        assertThat(result.startsAt).isEqualTo(event.startsAt)
        assertThat(result.capacity).isEqualTo(100)
        assertThat(result.availableTickets).isEqualTo(75)
        assertThat(result.createdAt).isEqualTo(createdAt)
    }

    // --------------------------------------------------
    // Cursor-based pagination
    // --------------------------------------------------

    @Test
    fun `should return next cursor when more events exist`() {
        // Given
        val first = createTestEvent(
            title = "Concert A",
            startsAt = fromStartsAt.plus(1, ChronoUnit.DAYS)
        )

        val second = createTestEvent(
            title = "Concert B",
            startsAt = fromStartsAt.plus(2, ChronoUnit.DAYS)
        )

        val third = createTestEvent(
            title = "Concert C",
            startsAt = fromStartsAt.plus(3, ChronoUnit.DAYS)
        )

        mockCursorQuery(
            events = listOf(first, second, third),
            requestedSize = 2
        )

        // When
        val response = getCursorPage(size = 2)

        // Then
        assertThat(response.content).hasSize(2)
        assertThat(response.size).isEqualTo(2)
        assertThat(response.hasNext).isTrue()

        assertThat(response.content.map { it.id })
            .containsExactly(first.id, second.id)

        assertThat(response.nextCursor)
            .isEqualTo(
                EventCursor(
                    startsAt = second.startsAt,
                    id = second.id
                )
            )
    }

    @Test
    fun `should exclude extra fetched event from response`() {
        // Given
        val events = listOf(
            createTestEvent(title = "Concert A"),
            createTestEvent(title = "Concert B"),
            createTestEvent(title = "Concert C")
        )

        mockCursorQuery(
            events = events,
            requestedSize = 2
        )

        // When
        val response = getCursorPage(size = 2)

        // Then
        assertThat(response.content.map { it.id })
            .containsExactly(events[0].id, events[1].id)

        assertThat(response.content.map { it.id })
            .doesNotContain(events[2].id)

        assertThat(response.nextCursor?.id)
            .isEqualTo(events[1].id)
    }

    @Test
    fun `should return no next cursor on final page`() {
        // Given
        val events = listOf(
            createTestEvent(title = "Concert A"),
            createTestEvent(title = "Concert B")
        )

        mockCursorQuery(
            events = events,
            requestedSize = 5
        )

        // When
        val response = getCursorPage(size = 5)

        // Then
        assertThat(response.content).hasSize(2)
        assertThat(response.size).isEqualTo(2)
        assertThat(response.hasNext).isFalse()
        assertThat(response.nextCursor).isNull()
    }

    @Test
    fun `should return no next cursor when result matches requested size exactly`() {
        // Given
        val events = listOf(
            createTestEvent(title = "Concert A"),
            createTestEvent(title = "Concert B")
        )

        mockCursorQuery(
            events = events,
            requestedSize = 2
        )

        // When
        val response = getCursorPage(size = 2)

        // Then
        assertThat(response.content).hasSize(2)
        assertThat(response.size).isEqualTo(2)
        assertThat(response.hasNext).isFalse()
        assertThat(response.nextCursor).isNull()
    }

    @Test
    fun `should return empty cursor response when no events exist`() {
        // Given
        mockCursorQuery(
            events = emptyList(),
            requestedSize = 20
        )

        // When
        val response = getCursorPage(size = 20)

        // Then
        assertThat(response.content).isEmpty()
        assertThat(response.size).isZero()
        assertThat(response.hasNext).isFalse()
        assertThat(response.nextCursor).isNull()
    }

    @Test
    fun `should support requesting the next page with a cursor`() {
        // Given
        val previousEvent = createTestEvent(
            startsAt = fromStartsAt.plus(1, ChronoUnit.DAYS)
        )

        val nextEvent = createTestEvent(
            startsAt = fromStartsAt.plus(2, ChronoUnit.DAYS)
        )

        val cursor = EventCursor(
            startsAt = previousEvent.startsAt,
            id = previousEvent.id
        )

        mockCursorQuery(
            events = listOf(nextEvent),
            requestedSize = 2
        )

        // When
        val response = getCursorPage(
            size = 2,
            cursor = cursor
        )

        // Then
        assertThat(response.content.map { it.id })
            .containsExactly(nextEvent.id)

        assertThat(response.hasNext).isFalse()
        assertThat(response.nextCursor).isNull()
    }

    @Test
    fun `should sort cursor query by startsAt and id and fetch one extra event`() {
        // Given
        val query = mockCursorQuery(
            events = emptyList(),
            requestedSize = 20
        )

        val expectedSort = Sort.by(
            Sort.Order.asc("startsAt"),
            Sort.Order.asc("id")
        )

        // When
        getCursorPage(size = 20)

        // Then
        verify(exactly = 1) {
            query.sortBy(expectedSort)
        }

        verify(exactly = 1) {
            query.limit(21)
        }

        verify(exactly = 1) {
            query.all()
        }
    }

    // --------------------------------------------------
    // Cursor validation
    // --------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = [-10, -1, 0, 101, 500])
    fun `should reject invalid cursor page sizes`(size: Int) {
        // When
        val exception = assertThrows<IllegalArgumentException> {
            getCursorPage(size = size)
        }

        // Then
        assertThat(exception.message)
            .isEqualTo("Size must be between 1 and 100")

        verify {
            eventRepository wasNot Called
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 100])
    fun `should accept valid cursor page size boundaries`(size: Int) {
        // Given
        val query = mockCursorQuery(
            events = emptyList(),
            requestedSize = size
        )

        // When
        val response = getCursorPage(size = size)

        // Then
        assertThat(response.content).isEmpty()

        verify(exactly = 1) {
            query.limit(size + 1)
        }
    }

    @Test
    fun `should reject date range when start is after end`() {
        // When
        val exception = assertThrows<IllegalArgumentException> {
            eventService.getEventsCursor(
                city = null,
                venue = null,
                name = null,
                availableOnly = null,
                fromStartsAt = toStartsAt,
                toStartsAt = fromStartsAt
            )
        }

        // Then
        assertThat(exception.message)
            .isEqualTo("fromStartsAt must be before toStartsAt")

        verify {
            eventRepository wasNot Called
        }
    }

    @Test
    fun `should reject date range when start equals end`() {
        // When
        val exception = assertThrows<IllegalArgumentException> {
            eventService.getEventsCursor(
                city = null,
                venue = null,
                name = null,
                availableOnly = null,
                fromStartsAt = fromStartsAt,
                toStartsAt = fromStartsAt
            )
        }

        // Then
        assertThat(exception.message)
            .isEqualTo("fromStartsAt must be before toStartsAt")

        verify {
            eventRepository wasNot Called
        }
    }

    // --------------------------------------------------
    // Event creation
    // --------------------------------------------------

    @Test
    fun `should create event with available tickets equal to capacity`() {
        // Given
        val request = CreateEventRequest(
            title = "Rock Concert",
            venue = "TD Garden",
            city = "Boston",
            startsAt = fromStartsAt,
            capacity = 100
        )

        val eventSlot = slot<Event>()

        every {
            eventRepository.saveAndFlush(capture(eventSlot))
        } answers {
            firstArg<Event>().also {
                ReflectionTestUtils.setField(
                    it,
                    "createdAt",
                    createdAt
                )
            }
        }

        // When
        val response = eventService.createEvent(request)

        // Then
        val savedEvent = eventSlot.captured

        assertThat(savedEvent.title).isEqualTo(request.title)
        assertThat(savedEvent.venue).isEqualTo(request.venue)
        assertThat(savedEvent.city).isEqualTo(request.city)
        assertThat(savedEvent.startsAt).isEqualTo(request.startsAt)
        assertThat(savedEvent.capacity).isEqualTo(100)
        assertThat(savedEvent.availableTickets).isEqualTo(100)

        assertThat(response.id).isEqualTo(savedEvent.id)
        assertThat(response.title).isEqualTo(request.title)
        assertThat(response.venue).isEqualTo(request.venue)
        assertThat(response.city).isEqualTo(request.city)
        assertThat(response.startsAt).isEqualTo(request.startsAt)
        assertThat(response.capacity).isEqualTo(100)
        assertThat(response.availableTickets).isEqualTo(100)
        assertThat(response.createdAt).isEqualTo(createdAt)

        verify(exactly = 1) {
            eventRepository.saveAndFlush(any())
        }
    }

    @Test
    fun `should save event using repository`() {
        // Given
        val event = createTestEvent()

        every {
            eventRepository.saveAndFlush(event)
        } returns event

        // When
        val savedEvent = eventService.saveEvent(event)

        // Then
        assertThat(savedEvent).isSameAs(event)

        verify(exactly = 1) {
            eventRepository.saveAndFlush(event)
        }
    }

    // --------------------------------------------------
    // Test helpers
    // --------------------------------------------------

    private fun createTestEvent(
        id: UUID = UUID.randomUUID(),
        title: String = "Test Concert",
        venue: String = "TD Garden",
        city: String = "Boston",
        startsAt: Instant = fromStartsAt,
        capacity: Int = 100,
        availableTickets: Int = capacity
    ): Event {

        return Event(
            id = id,
            title = title,
            venue = venue,
            city = city,
            startsAt = startsAt,
            capacity = capacity,
            availableTickets = availableTickets
        ).also {
            ReflectionTestUtils.setField(
                it,
                "createdAt",
                createdAt
            )
        }
    }

    private fun getCursorPage(
        size: Int = 20,
        cursor: EventCursor? = null
    ) = eventService.getEventsCursor(
        city = null,
        venue = null,
        name = null,
        availableOnly = null,
        fromStartsAt = fromStartsAt,
        toStartsAt = toStartsAt,
        size = size,
        cursor = cursor
    )

    private fun mockCursorQuery(
        events: List<Event>,
        requestedSize: Int
    ): SpecificationFluentQuery<Event> {

        val query = mockk<SpecificationFluentQuery<Event>>()

        val expectedSort = Sort.by(
            Sort.Order.asc("startsAt"),
            Sort.Order.asc("id")
        )

        every {
            query.sortBy(expectedSort)
        } returns query

        every {
            query.limit(requestedSize + 1)
        } returns query

        every {
            query.all()
        } returns events

        every {
            eventRepository.findBy<Event, List<Event>>(
                any<Specification<Event>>(),
                any()
            )
        } answers {

            val queryFunction =
                secondArg<
                        Function<
                                SpecificationFluentQuery<Event>,
                                List<Event>
                                >
                        >()

            queryFunction.apply(query)
        }

        return query
    }
}