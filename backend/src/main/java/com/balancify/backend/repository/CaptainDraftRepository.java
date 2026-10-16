package com.balancify.backend.repository;

import com.balancify.backend.domain.CaptainDraft;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaptainDraftRepository extends JpaRepository<CaptainDraft, Long> {

    Optional<CaptainDraft> findByIdAndGroup_Id(Long draftId, Long groupId);

    Optional<CaptainDraft> findTopByGroup_IdOrderByCreatedAtDescIdDesc(Long groupId);

    @Query("""
        select draft
        from CaptainDraft draft
        join fetch draft.homeCaptain
        join fetch draft.awayCaptain
        where draft.group.id = :groupId
        order by draft.createdAt desc, draft.id desc
        """)
    List<CaptainDraft> findRecentWithCaptains(@Param("groupId") Long groupId, Pageable pageable);

    // Last of a draft's rows to go, after its sets and participants; nothing loaded is kept after it.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CaptainDraft draft where draft.id = :draftId")
    int deleteDraft(@Param("draftId") Long draftId);
}
