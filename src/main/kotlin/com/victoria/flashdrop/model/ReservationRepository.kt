package com.victoria.flashdrop.model

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ReservationRepository : JpaRepository<Reservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: UUID
    ): Reservation?

    @Query(
        """
    SELECT r.id
    FROM Reservation r
    WHERE r.status = :status
    AND r.expiresAt <= :now
    ORDER BY r.expiresAt ASC, r.id ASC
    """
    )
    fun findExpiredReservationIds(
        @Param("status") status: ReservationStatus,
        @Param("now") now: Instant,
        pageable: Pageable
    ): List<UUID>

}