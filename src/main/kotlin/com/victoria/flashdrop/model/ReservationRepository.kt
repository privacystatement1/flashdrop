package com.victoria.flashdrop.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReservationRepository : JpaRepository<Reservation, UUID>