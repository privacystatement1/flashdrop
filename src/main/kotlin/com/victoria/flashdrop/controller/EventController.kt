package com.victoria.flashdrop.controller

import com.victoria.flashdrop.service.EventService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/events")
class EventController(private val eventService: EventService) {

    @GetMapping("")
    fun getEvents(): List<EventResponse>? {
        return eventService.getEvents()
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createEvent(
        @Valid @RequestBody request: CreateEventRequest
    ): EventResponse {
        return eventService.createEvent(request)
    }
}