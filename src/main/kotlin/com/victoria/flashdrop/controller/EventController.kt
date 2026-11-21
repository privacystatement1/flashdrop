package com.victoria.flashdrop.controller

import com.victoria.flashdrop.model.Event
import com.victoria.flashdrop.service.EventService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/events")
class EventController(private val eventService: EventService) {

    @GetMapping("")
    fun getEvents(): List<Event>? {
        return eventService.getEvents()
    }
}