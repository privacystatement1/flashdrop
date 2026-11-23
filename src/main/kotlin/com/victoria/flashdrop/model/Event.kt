package com.victoria.flashdrop.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.generator.EventType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "events")
class Event(

    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "venue", nullable = false)
    var venue: String,

    @Column(name = "city", nullable = false)
    var city: String,

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant,

    @Column(name = "capacity", nullable = false)
    var capacity: Int,

    @Column(name = "available_tickets", nullable = false)
    var availableTickets: Int,

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