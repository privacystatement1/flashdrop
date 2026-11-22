CREATE TABLE events (
                        id UUID PRIMARY KEY,
                        title VARCHAR NOT NULL,
                        venue VARCHAR NOT NULL,
                        city VARCHAR NOT NULL,
                        starts_at TIMESTAMPTZ NOT NULL,
                        capacity INTEGER NOT NULL,
                        available_tickets INTEGER NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                        CONSTRAINT events_capacity_check
                            CHECK (capacity > 0),

                        CONSTRAINT events_available_tickets_check
                            CHECK (available_tickets >= 0
                                AND available_tickets <= capacity)
);