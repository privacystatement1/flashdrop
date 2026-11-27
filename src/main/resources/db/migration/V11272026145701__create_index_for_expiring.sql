CREATE INDEX idx_reservations_status_expires_at_id
    ON reservations (status, expires_at, id);