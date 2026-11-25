package com.victoria.flashdrop.controller

import com.victoria.flashdrop.service.EventService
import jakarta.validation.Valid
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/events")
class EventController(private val eventService: EventService) {

    @GetMapping("")
    fun getEvents(
        @RequestParam(required = false) city: String?,
        @RequestParam(required = false) venue: String?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) availableOnly: Boolean?,
        @RequestParam(required = false) fromStartsAt: Instant?,
        @RequestParam(required = false) toStartsAt: Instant?,
        @PageableDefault(
            size = 10,
            page = 0,
            sort = ["startsAt"],
            direction = Sort.Direction.ASC
        )
        pageable: Pageable
    ): PaginatedResponse<EventResponse> {
        return eventService.getEvents(
            city,
            venue,
            name,
            availableOnly,
            fromStartsAt,
            toStartsAt,
            pageable
        )
    }

    @GetMapping("/cursor")
    fun getEventsCursorBasedPagination(
        @RequestParam(required = false) city: String?,
        @RequestParam(required = false) venue: String?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) availableOnly: Boolean?,
        @RequestParam(required = true) fromStartsAt: Instant,
        @RequestParam(required = true) toStartsAt: Instant,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) cursorStartsAt: Instant?,
        @RequestParam(required = false) cursorId: UUID?

    ): CursorPaginatedResponse<EventResponse> {
        if ((cursorStartsAt == null) != (cursorId == null)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Both cursorStartsAt and cursorId must be provided"
            )
        }

        val cursor = if (cursorStartsAt != null && cursorId != null) {
            EventCursor(cursorStartsAt, cursorId)
        } else {
            null
        }

        return eventService.getEventsCursor(
            city = city,
            venue = venue,
            name = name,
            availableOnly = availableOnly,
            fromStartsAt = fromStartsAt,
            toStartsAt = toStartsAt,
            size = size,
            cursor = cursor
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createEvent(
        @Valid @RequestBody request: CreateEventRequest
    ): EventResponse {
        return eventService.createEvent(request)
    }
}