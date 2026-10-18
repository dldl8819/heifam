package com.balancify.backend.repository;

import com.balancify.backend.domain.CaptainDraftLineup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// A draft removed takes its line-ups with it by the foreign key's cascade (V82).
public interface CaptainDraftLineupRepository extends JpaRepository<CaptainDraftLineup, Long> {

    @Query("""
        select lineup
        from CaptainDraftLineup lineup
        where lineup.draft.id = :draftId
        order by lineup.stage asc, lineup.team asc
        """)
    List<CaptainDraftLineup> findByDraftId(@Param("draftId") Long draftId);

    // A draft handed over to its host keeps no line-ups to come back to.
    @Modifying(flushAutomatically = true)
    @Query("delete from CaptainDraftLineup lineup where lineup.draft.id = :draftId")
    int deleteByDraftId(@Param("draftId") Long draftId);
}
