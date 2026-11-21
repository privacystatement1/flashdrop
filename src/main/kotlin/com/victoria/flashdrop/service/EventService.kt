package com.victoria.flashdrop.service

import com.victoria.flashdrop.model.Event
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EventService {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(EventService::class.java)
    }

    fun getEvents(): List<Event> {
        log.info("Getting events")
        return listOf(Event("new Event"))
    }

}