package com.balancify.backend.repository;

import com.balancify.backend.domain.Player;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByGroup_IdOrderByMmrDescIdAsc(Long groupId);

    List<Player> findByGroup_IdAndNicknameIgnoreCase(Long groupId, String nickname);

    List<Player> findByNicknameIgnoreCaseAndAnonymizedAtIsNull(String nickname);

    List<Player> findByAuthUserIdAndAnonymizedAtIsNull(UUID authUserId);

    @Query("""
        select p
        from Player p
        where p.active = false
          and p.anonymizedAt is null
          and (p.identityRetainedUntil is null or p.identityRetainedUntil <= :retainedUntil)
        order by p.id
        """)
    List<Player> findIdentityRetentionExpiryCandidates(
        @Param("retainedUntil") OffsetDateTime retainedUntil
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        update public.players as target
        set active = true,
            chat_left_at = null,
            chat_left_reason = null,
            chat_rejoined_at = :rejoinedAt,
            lifecycle_status = 'ACTIVE',
            identity_retained_until = null
        where target.id = :playerId
          and target.group_id = :groupId
          and target.active = false
          and target.anonymized_at is null
          and target.lifecycle_status = 'INACTIVE'
          and target.identity_retained_until > :now
          and not exists (
              select 1
              from public.players other
              where other.group_id = target.group_id
                and other.id <> target.id
                and other.active = true
                and other.anonymized_at is null
                and lower(btrim(other.nickname)) = lower(btrim(target.nickname))
          )
        """, nativeQuery = true)
    int reactivateRetainedInactivePlayer(
        @Param("groupId") Long groupId,
        @Param("playerId") Long playerId,
        @Param("rejoinedAt") OffsetDateTime rejoinedAt,
        @Param("now") OffsetDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        update public.players
        set auth_user_id = null,
            nickname = :hiddenMemberLabel,
            note = null,
            active = false,
            chat_left_at = null,
            chat_left_reason = null,
            chat_rejoined_at = null,
            tier_change_acknowledged_tier = null,
            tier_change_acknowledged_at = null,
            anonymized_at = :anonymizedAt,
            lifecycle_status = 'ANONYMIZED',
            identity_retained_until = null
        where id = :playerId
          and active = false
          and anonymized_at is null
          and (identity_retained_until is null or identity_retained_until <= :anonymizedAt)
        """, nativeQuery = true)
    int anonymizeExpiredIdentity(
        @Param("playerId") Long playerId,
        @Param("hiddenMemberLabel") String hiddenMemberLabel,
        @Param("anonymizedAt") OffsetDateTime anonymizedAt
    );

    boolean existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndIdNot(
        UUID authUserId,
        Long excludedPlayerId
    );

    boolean existsByNicknameIgnoreCaseAndActiveTrueAndAnonymizedAtIsNullAndIdNot(
        String nickname,
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
