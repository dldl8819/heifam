package com.balancify.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** How far an account has read its notifications. */
@Entity
@Table(name = "notification_cursors")
public class NotificationCursor {

    @Id
    @Column(length = 320)
    private String email;

    @Column(name = "last_read_id", nullable = false)
    private long lastReadId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public long getLastReadId() {
        return lastReadId;
    }

    public void setLastReadId(long lastReadId) {
        this.lastReadId = lastReadId;
        this.updatedAt = OffsetDateTime.now();
    }
}
