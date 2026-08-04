package com.balancify.backend.repository;

import com.balancify.backend.domain.Player;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByGroup_IdOrderByMmrDescIdAsc(Long groupId);

    List<Player> findByGroup_IdAndNicknameIgnoreCase(Long groupId, String nickname);

    List<Player> findByNicknameIgnoreCaseAndAnonymizedAtIsNull(String nickname);

    List<Player> findByAuthUserIdAndAnonymizedAtIsNull(UUID authUserId);
    boolean existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndIdNot(
        UUID authUserId,
        Long excludedPlayerId
    );

    Optional<Player> findByIdAndGroup_Id(Long playerId, Long groupId);

    boolean existsByIdAndGroup_IdAndActiveTrueAndAnonymizedAtIsNull(Long playerId, Long groupId);

    @Query("""
        select
            p.id as playerId,
            p.nickname as nickname,
            p.createdAt as createdAt
        from Player p
        where p.group.id = :groupId
          and p.active = true
          and p.anonymizedAt is null
        order by p.mmr desc, p.id asc
        """)
    List<PlayerActivityCandidateProjection> findActivityCandidatesByGroupId(
        @Param("groupId") Long groupId
    );

    List<Player> findByGroup_IdAndIdIn(Long groupId, List<Long> playerIds);

    interface PlayerActivityCandidateProjection {
        Long getPlayerId();
        String getNickname();
        OffsetDateTime getCreatedAt();
    }
}
