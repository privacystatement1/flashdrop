package com.victoria.flashdrop.mapper

import com.victoria.flashdrop.controller.CreateEventRequest
import com.victoria.flashdrop.controller.EventResponse
import com.victoria.flashdrop.model.Event


fun CreateEventRequest.toEntity(): Event =
    Event(
        title = title,
        venue = venue,
        city = city,
        startsAt = startsAt,
        capacity = capacity,
        availableTickets = capacity
    )

fun Event.toResponse(): EventResponse =
    EventResponse(
        id = id,
        title = title,
        venue = venue,
        city = city,
        startsAt = startsAt,
        capacity = capacity,
        availableTickets = availableTickets,
        createdAt = checkNotNull(createdAt) {
            "Event createdAt has not been loaded"
        }
    )