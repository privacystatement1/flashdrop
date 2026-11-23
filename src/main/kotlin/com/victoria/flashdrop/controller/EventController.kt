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

@RestController
@RequestMapping("/events")
class EventController(private val eventService: EventService) {

    @GetMapping("")
    fun getEvents(
        @RequestParam(required = false) city: String?,
        @RequestParam(required = false) venue: String?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) availableOnly: Boolean?,
        @PageableDefault(
            size = 10,
            page = 0,
            sort = ["startsAt"],
            direction = Sort.Direction.ASC
        )
        pageable: Pageable
    ): PaginatedResponse<EventResponse> {
        return eventService.getEvents(city, venue, name, availableOnly, pageable)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createEvent(
        @Valid @RequestBody request: CreateEventRequest
    ): EventResponse {
        return eventService.createEvent(request)
    }
}