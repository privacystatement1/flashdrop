package com.victoria.flashdrop.service

import com.victoria.flashdrop.controller.CreateEventRequest
import com.victoria.flashdrop.controller.CursorPaginatedResponse
import com.victoria.flashdrop.controller.EventCursor
import com.victoria.flashdrop.controller.EventResponse
import com.victoria.flashdrop.controller.PaginatedResponse
import com.victoria.flashdrop.mapper.toEntity
import com.victoria.flashdrop.mapper.toResponse
import com.victoria.flashdrop.model.Event
import com.victoria.flashdrop.model.EventRepository
import jakarta.persistence.criteria.Predicate
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class EventService(
    private val eventRepository: EventRepository,
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(EventService::class.java)
    }

    @Transactional(readOnly = true)
    fun getEvents(
        city: String?,
        venue: String?,
        name: String?,
        availableOnly: Boolean?,
        fromStartsAt: Instant?,
        toStartsAt: Instant?,
        pageable: Pageable
    ): PaginatedResponse<EventResponse> {

        val specification = Specification<Event> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (!city.isNullOrBlank()) {
                predicates.add(
                    cb.equal(
                        cb.lower(root.get("city")),
                        city.lowercase()
                    )
                )
            }

            if (!venue.isNullOrBlank()) {
                predicates.add(
                    cb.like(
                        cb.lower(root.get("venue")),
                        "%${venue.lowercase()}%"
                    )
                )
            }

            if (!name.isNullOrBlank()) {
                predicates.add(
                    cb.like(
                        cb.lower(root.get("title")),
                        "%${name.lowercase()}%"
                    )
                )
            }

            if (toStartsAt != null && fromStartsAt != null) {
                predicates.add(
                    cb.between(
                        root.get<Instant>("startsAt"),
                        fromStartsAt,
                        toStartsAt
                    )
                )
            }

            if (availableOnly == true) {
                predicates.add(
                    cb.greaterThan(root.get<Int>("availableTickets"), 0)
                )
            }

            cb.and(*predicates.toTypedArray())
        }
        val pages = eventRepository.findAll(specification, pageable)
            .map { it.toResponse() }

        return PaginatedResponse(
            content = pages.content,
            page = pages.number,
            size = pages.size,
            totalElements = pages.totalElements,
            totalPages = pages.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun getEventsCursor(
        city: String?,
        venue: String?,
        name: String?,
        availableOnly: Boolean?,
        fromStartsAt: Instant,
        toStartsAt: Instant,
        size: Int = 20,
        cursor: EventCursor? = null
    ): CursorPaginatedResponse<EventResponse> {

        require(size in 1..100) {
            "Size must be between 1 and 100"
        }

        require(fromStartsAt.isBefore(toStartsAt)) {
            "fromStartsAt must be before toStartsAt"
        }

        val specification = Specification<Event> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (!city.isNullOrBlank()) {
                predicates.add(
                    cb.equal(
                        cb.lower(root.get("city")),
                        city.lowercase()
                    )
                )
            }

            if (!venue.isNullOrBlank()) {
                predicates.add(
                    cb.like(
                        cb.lower(root.get("venue")),
                        "%${venue.lowercase()}%"
                    )
                )
            }

            if (!name.isNullOrBlank()) {
                predicates.add(
                    cb.like(
                        cb.lower(root.get("title")),
                        "%${name.lowercase()}%"
                    )
                )
            }

            predicates.add(
                cb.greaterThanOrEqualTo(
                    root.get<Instant>("startsAt"),
                    fromStartsAt
                )
            )

            predicates.add(
                cb.lessThan(
                    root.get<Instant>("startsAt"),
                    toStartsAt
                )
            )

            if (availableOnly == true) {
                predicates.add(
                    cb.greaterThan(
                        root.get<Int>("availableTickets"),
                        0
                    )
                )
            }

            // Cursor-based pagination
            if (cursor != null) {
                predicates.add(
                    cb.or(
                        cb.greaterThan(
                            root.get<Instant>("startsAt"),
                            cursor.startsAt
                        ),
                        cb.and(
                            cb.equal(
                                root.get<Instant>("startsAt"),
                                cursor.startsAt
                            ),
                            cb.greaterThan(
                                root.get<UUID>("id"),
                                cursor.id
                            )
                        )
                    )
                )
            }

            cb.and(*predicates.toTypedArray())
        }

        val sort = Sort.by(
            Sort.Order.asc("startsAt"),
            Sort.Order.asc("id")
        )

        val events = eventRepository.findBy<Event, List<Event>>(
            specification
        ) { query ->
            query
                .sortBy(sort)
                .limit(size + 1)
                .all()
        }

        val hasNext = events.size > size
        val content = events.take(size)

        val nextCursor = if (hasNext) {
            content.last().let {
                EventCursor(
                    startsAt = it.startsAt,
                    id = requireNotNull(it.id)
                )
            }
        } else {
            null
        }

        return CursorPaginatedResponse(
            content = content.map { it.toResponse() },
            size = content.size,
            hasNext = hasNext,
            nextCursor = nextCursor
        )
    }

    @Transactional
    fun createEvent(
        request: CreateEventRequest
    ): EventResponse {
        val savedEntity = saveEvent(request.toEntity())
        return savedEntity.toResponse()
    }

    fun saveEvent(event: Event): Event {
        return eventRepository.saveAndFlush(event)
    }

}