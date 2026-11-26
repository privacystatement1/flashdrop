CREATE TABLE reservations (
                              id UUID PRIMARY KEY,
                              event_id UUID NOT NULL,
                              quantity INTEGER NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'HELD',
                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              expires_at TIMESTAMPTZ NOT NULL,

                              CONSTRAINT fk_reservations_event
                                  FOREIGN KEY (event_id) REFERENCES events(id),

                              CONSTRAINT chk_reservations_quantity
                                  CHECK (quantity BETWEEN 1 AND 5)
);