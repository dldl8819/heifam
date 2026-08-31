package com.balancify.backend.repository;

import com.balancify.backend.domain.Notice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);

    Optional<Notice> findByIdAndGroupId(Long id, Long groupId);
}
