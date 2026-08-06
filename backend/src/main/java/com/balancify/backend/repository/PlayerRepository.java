package com.balancify.backend.repository;

import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByGroup_IdOrderByMmrDescIdAsc(Long groupId);

    List<Player> findByGroup_IdAndNicknameIgnoreCase(Long groupId, String nickname);

    List<Player> findByNicknameIgnoreCaseAndAnonymizedAtIsNull(String nickname);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Player> findByAuthUserIdAndAnonymizedAtIsNull(UUID authUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Player> findByRetentionSubjectHashAndAnonymizedAtIsNull(String retentionSubjectHash);

    @Query("""
        select distinct p.authUserId
        from Player p
        where p.authUserId is not null
          and p.active = true
          and p.anonymizedAt is null
          and p.lifecycleStatus = com.balancify.backend.domain.PlayerLifecycleStatus.ACTIVE
        """)
    List<UUID> findDistinctActiveAuthUserIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Player p where p.id = :playerId")
    Optional<Player> findByIdForIdentityUpdate(@Param("playerId") Long playerId);

    @Query(value = """
        select p.*
        from public.players p
        where p.anonymized_at is null
          and (
              (
                  p.lifecycle_status = 'INACTIVE'
                  and (
                  p.active = true
                      or p.anonymized_at is not null
                      or p.auth_user_id is not null
                      or p.chat_left_at is null
                      or p.chat_left_at > :retainedUntil
                      or p.chat_left_reason is null
                      or p.chat_left_reason not in ('장기 미참여', '본인 요청', '운영 정책', '기타')
                      or nullif(btrim(p.nickname), '') is null
                      or p.nickname = :hiddenMemberLabel
                      or p.note is not null
                      or p.chat_rejoined_at is not null
                      or p.tier_change_acknowledged_tier is not null
                      or p.tier_change_acknowledged_at is not null
                      or (
                          p.retention_subject_hash is not null
                          and p.retention_subject_hash !~ '^[0-9a-f]{64}$'
                      )
                      or p.identity_retained_until is null
                      or p.identity_retained_until <= :retainedUntil
                      or p.identity_retained_until <= p.chat_left_at
                      or p.identity_retained_until > p.chat_left_at + interval '1 year'
                  )
              )
              or (
                  p.lifecycle_status is distinct from 'INACTIVE'
                  and (
                      p.active = false
                      or p.anonymized_at is not null
                      or p.lifecycle_status is distinct from 'ACTIVE'
                      or p.identity_retained_until is not null
                      or p.retention_subject_hash is not null
                  )
              )
          )
        order by p.id
        """, nativeQuery = true)
    List<Player> findIdentityRetentionExpiryCandidates(
        @Param("retainedUntil") OffsetDateTime retainedUntil,
        @Param("hiddenMemberLabel") String hiddenMemberLabel
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        update public.players as target
        set active = true,
            auth_user_id = :authUserId,
            chat_left_at = null,
            chat_left_reason = null,
            chat_rejoined_at = :rejoinedAt,
            lifecycle_status = 'ACTIVE',
            identity_retained_until = null,
            retention_subject_hash = null,
            version = version + 1
        where target.id = :playerId
          and target.group_id = :groupId
          and target.active = false
          and target.anonymized_at is null
          and target.lifecycle_status = 'INACTIVE'
          and target.identity_retained_until > :now
          and target.retention_subject_hash is not distinct from :expectedRetentionSubjectHash
          and (
              :authUserId is null
              or exists (
                  select 1
                  from public.players account_link
                  where account_link.auth_user_id = :authUserId
                    and account_link.active = true
                    and account_link.anonymized_at is null
                    and account_link.lifecycle_status = 'ACTIVE'
              )
          )
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
        @Param("now") OffsetDateTime now,
        @Param("expectedRetentionSubjectHash") String expectedRetentionSubjectHash,
        @Param("authUserId") UUID authUserId
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
            identity_retained_until = null,
            retention_subject_hash = null,
            version = version + 1
        where id = :playerId
          and anonymized_at is null
          and (
              (
                  lifecycle_status = 'INACTIVE'
                  and (
                  active = true
                      or anonymized_at is not null
                      or auth_user_id is not null
                      or chat_left_at is null
                      or chat_left_at > :anonymizedAt
                      or chat_left_reason is null
                      or chat_left_reason not in ('장기 미참여', '본인 요청', '운영 정책', '기타')
                      or nullif(btrim(nickname), '') is null
                      or nickname = :hiddenMemberLabel
                      or note is not null
                      or chat_rejoined_at is not null
                      or tier_change_acknowledged_tier is not null
                      or tier_change_acknowledged_at is not null
                      or (
                          retention_subject_hash is not null
                          and retention_subject_hash !~ '^[0-9a-f]{64}$'
                      )
                      or identity_retained_until is null
                      or identity_retained_until <= :anonymizedAt
                      or identity_retained_until <= chat_left_at
                      or identity_retained_until > chat_left_at + interval '1 year'
                  )
              )
              or (
                  lifecycle_status is distinct from 'INACTIVE'
                  and (
                      active = false
                      or anonymized_at is not null
                      or lifecycle_status is distinct from 'ACTIVE'
                      or identity_retained_until is not null
                      or retention_subject_hash is not null
                  )
              )
          )
        """, nativeQuery = true)
    int anonymizeExpiredIdentity(
        @Param("playerId") Long playerId,
        @Param("hiddenMemberLabel") String hiddenMemberLabel,
        @Param("anonymizedAt") OffsetDateTime anonymizedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Player p
        set p.retentionSubjectHash = null,
            p.version = p.version + 1
        where p.retentionSubjectHash = :retentionSubjectHash
        """)
    int clearRetentionSubjectHash(@Param("retentionSubjectHash") String retentionSubjectHash);

    boolean existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndLifecycleStatusAndIdNot(
        UUID authUserId,
        PlayerLifecycleStatus lifecycleStatus,
        Long excludedPlayerId
    );

    Optional<Player> findByIdAndGroup_Id(Long playerId, Long groupId);

    boolean existsByIdAndGroup_IdAndActiveTrueAndAnonymizedAtIsNullAndLifecycleStatus(
        Long playerId,
        Long groupId,
        PlayerLifecycleStatus lifecycleStatus
    );

    @Query("""
        select
            p.id as playerId,
            p.nickname as nickname,
            p.createdAt as createdAt
        from Player p
        where p.group.id = :groupId
          and p.active = true
          and p.anonymizedAt is null
          and p.lifecycleStatus = com.balancify.backend.domain.PlayerLifecycleStatus.ACTIVE
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
