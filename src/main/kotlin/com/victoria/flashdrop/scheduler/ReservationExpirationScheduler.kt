package com.victoria.flashdrop.scheduler

import com.victoria.flashdrop.model.ReservationRepository
import com.victoria.flashdrop.model.ReservationStatus
import com.victoria.flashdrop.service.ReservationService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ReservationExpirationScheduler(
    private val reservationRepository: ReservationRepository,
    private val reservationService: ReservationService
) {

    private val log = LoggerFactory.getLogger(
        ReservationExpirationScheduler::class.java
    )

    @Scheduled(fixedDelay = 10_000)
    fun expireReservations() {

        val reservationIds =
            reservationRepository.findExpiredReservationIds(
                status = ReservationStatus.HELD,
                now = Instant.now(),
                pageable = PageRequest.of(0, 100)
            )

        reservationIds.forEach { reservationId ->

            try {
                val expired = reservationService
                    .expireReservation(reservationId)

                if (expired) {
                    log.info(
                        "Expired reservation {}",
                        reservationId
                    )
                }

            } catch (exception: Exception) {
                log.error(
                    "Failed to expire reservation {}",
                    reservationId,
                    exception
                )
            }
        }
    }
}