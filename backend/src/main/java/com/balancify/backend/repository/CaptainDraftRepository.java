package com.balancify.backend.repository;

import com.balancify.backend.domain.CaptainDraft;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaptainDraftRepository extends JpaRepository<CaptainDraft, Long> {

    Optional<CaptainDraft> findByIdAndGroup_Id(Long draftId, Long groupId);

    Optional<CaptainDraft> findTopByGroup_IdOrderByCreatedAtDescIdDesc(Long groupId);
}
