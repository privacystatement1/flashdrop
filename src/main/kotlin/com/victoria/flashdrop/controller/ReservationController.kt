package com.victoria.flashdrop.controller

import com.victoria.flashdrop.controller.request.CreateReservationRequest
import com.victoria.flashdrop.controller.response.ReservationResponse
import com.victoria.flashdrop.service.ReservationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/reservations")
class ReservationController(private val reservationService: ReservationService) {


    @GetMapping("/{id}")
    fun getReservation(
        @PathVariable("id") id: UUID
    ): ReservationResponse {
        return reservationService.getReservation(id)
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createEvent(
        @Valid @RequestBody request: CreateReservationRequest
    ): ReservationResponse {
        return reservationService.createReservation(request)
    }
}