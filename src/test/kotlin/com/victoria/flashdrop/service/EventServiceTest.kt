package com.victoria.flashdrop.service

import com.victoria.flashdrop.model.EventRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito.mock

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventServiceTest {
    private val eventRepository: EventRepository = mock()
    private val eventService: EventService = EventService(eventRepository)

    @Test
    fun `get first event`() {

    }
}