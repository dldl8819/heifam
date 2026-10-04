package com.balancify.backend.repository;

import com.balancify.backend.domain.NotificationCursor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationCursorRepository extends JpaRepository<NotificationCursor, String> {
}
