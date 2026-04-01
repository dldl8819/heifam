package com.balancify.backend.repository;

import com.balancify.backend.domain.CaptainDraftParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
