package com.balancify.backend.repository;

import com.balancify.backend.domain.CaptainDraftParticipant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaptainDraftParticipantRepository extends JpaRepository<CaptainDraftParticipant, Long> {

    @Query("""
        select participant
        from CaptainDraftParticipant participant
        join fetch participant.player player
        where participant.draft.id = :draftId
        """)
    List<CaptainDraftParticipant> findByDraftIdWithPlayer(@Param("draftId") Long draftId);

    interface DraftParticipantCount {
        Long getDraftId();

        Long getParticipants();
    }

    @Query("""
        select participant.draft.id as draftId, count(participant) as participants
        from CaptainDraftParticipant participant
        where participant.draft.id in :draftIds
        group by participant.draft.id
        """)
    List<DraftParticipantCount> countByDraftIds(@Param("draftIds") Collection<Long> draftIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from CaptainDraftParticipant participant where participant.draft.id = :draftId")
    int deleteByDraftId(@Param("draftId") Long draftId);
}
