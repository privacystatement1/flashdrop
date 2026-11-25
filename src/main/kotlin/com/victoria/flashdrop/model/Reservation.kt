package com.victoria.flashdrop.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "reservations")
class Reservation(

    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "event_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_reservations_event")
    )
    var event: Event,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ReservationStatus = ReservationStatus.HELD,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

) {
    @field:Generated(event = [EventType.INSERT])
    @Column(
        name = "created_at",
        nullable = false,
        insertable = false,
        updatable = false
    )
    var createdAt: Instant? = null
        protected set
}