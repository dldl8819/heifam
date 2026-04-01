package com.balancify.backend.repository;

import com.balancify.backend.domain.CaptainDraftEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaptainDraftEntryRepository extends JpaRepository<CaptainDraftEntry, Long> {

    @Query("""
        select entry
        from CaptainDraftEntry entry
        left join fetch entry.homePlayer homePlayer
        left join fetch entry.awayPlayer awayPlayer
        where entry.draft.id = :draftId
        order by entry.roundNumber asc, entry.setNumber asc
        """)
    List<CaptainDraftEntry> findByDraftIdWithPlayers(@Param("draftId") Long draftId);
}
