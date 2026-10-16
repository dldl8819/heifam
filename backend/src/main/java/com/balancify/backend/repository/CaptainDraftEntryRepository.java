package com.balancify.backend.repository;

import com.balancify.backend.domain.CaptainDraftEntry;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaptainDraftEntryRepository extends JpaRepository<CaptainDraftEntry, Long> {

    @Query("""
        select distinct entry
        from CaptainDraftEntry entry
        left join fetch entry.players slotted
        left join fetch slotted.player
        where entry.draft.id = :draftId
        order by entry.roundNumber asc, entry.setNumber asc
        """)
    List<CaptainDraftEntry> findByDraftIdWithPlayers(@Param("draftId") Long draftId);

    // The results of several drafts at once, for the list of records; players are not needed.
    @Query("""
        select entry
        from CaptainDraftEntry entry
        where entry.draft.id in :draftIds
        """)
    List<CaptainDraftEntry> findByDraftIds(@Param("draftIds") Collection<Long> draftIds);
}
