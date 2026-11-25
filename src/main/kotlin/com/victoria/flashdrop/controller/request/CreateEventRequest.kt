package com.victoria.flashdrop.controller.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.time.Instant

data class CreateEventRequest(
    @field:NotBlank
    val title: String,

    @field:NotBlank
    val venue: String,

    @field:NotBlank
    val city: String,

    val startsAt: Instant,

    @field:Positive
    val capacity: Int
)

