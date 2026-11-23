package com.victoria.flashdrop.service

import com.victoria.flashdrop.controller.CreateEventRequest
import com.victoria.flashdrop.controller.EventResponse
import com.victoria.flashdrop.mapper.toEntity
import com.victoria.flashdrop.mapper.toResponse
import com.victoria.flashdrop.model.Event
import com.victoria.flashdrop.model.EventRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService(
    private val eventRepository: EventRepository,
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(EventService::class.java)
    }

    fun getEvents(): List<EventResponse> {
        return eventRepository.findAll().map { it.toResponse() }
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