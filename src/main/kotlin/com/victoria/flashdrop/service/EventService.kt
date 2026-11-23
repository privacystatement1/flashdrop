package com.victoria.flashdrop.service

import com.victoria.flashdrop.controller.CreateEventRequest
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
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService(
    private val eventRepository: EventRepository,
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(EventService::class.java)
    }

    fun getEvents(
        city: String?,
        venue: String?,
        name: String?,
        availableOnly: Boolean?,
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