package com.victoria.flashdrop.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventServiceTest {

    private val eventService: EventService = EventService()

    @Test
    fun `get first event`() {
        val event = eventService.getEvents().first()
        event.apply {
            assertThat(name).isEqualTo("new Event")
        }
    }
}