package com.balancify.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.balancify.backend.service.PlayerRacePolicy;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(name = "auth_user_id")
    private UUID authUserId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 3)
    private String race = "P";

    @Column(length = 20)
    private String tier = PlayerTierPolicy.resolveTier(1000);

    @Column(name = "highest_achieved_tier", length = 20)
    private String highestAchievedTier;

    @Column(name = "base_mmr")
    private Integer baseMmr;

    @Column(nullable = false)
    private Integer mmr = 1000;

    @Column(length = 255)
    private String note;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "chat_left_at")
    private OffsetDateTime chatLeftAt;

    @Column(name = "chat_left_reason", length = 500)
    private String chatLeftReason;

    @Column(name = "chat_rejoined_at")
    private OffsetDateTime chatRejoinedAt;

    @Column(name = "tier_change_acknowledged_tier", length = 20)
    private String tierChangeAcknowledgedTier;

    @Column(name = "tier_change_acknowledged_at")
    private OffsetDateTime tierChangeAcknowledgedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "anonymized_at")
    private OffsetDateTime anonymizedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private PlayerLifecycleStatus lifecycleStatus = PlayerLifecycleStatus.ACTIVE;

    @Column(name = "identity_retained_until")
    private OffsetDateTime identityRetainedUntil;

    @Column(name = "retention_subject_hash", length = 64)
    @JsonIgnore
    private String retentionSubjectHash;

    @Column(name = "last_tier_recalculated_at")
    private OffsetDateTime lastTierRecalculatedAt;

    @Column(name = "last_tier_snapshot_at")
    private OffsetDateTime lastTierSnapshotAt;

    @Column(name = "last_tier_snapshot_mmr")
    private Integer lastTierSnapshotMmr;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public UUID getAuthUserId() {
        return authUserId;
    }

    public void setAuthUserId(UUID authUserId) {
        this.authUserId = authUserId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRace() {
        return race;
    }

    public void setRace(String race) {
        this.race = PlayerRacePolicy.normalizeCapabilityOrDefault(race, "P");
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
        recordHighestAchievedTier(tier);
    }

    public String getHighestAchievedTier() {
        return highestAchievedTier;
    }

    public void setHighestAchievedTier(String highestAchievedTier) {
        recordHighestAchievedTier(highestAchievedTier);
    }

    public Integer getMmr() {
        return mmr;
    }

    public void setMmr(Integer mmr) {
        this.mmr = normalizeMmr(mmr);
    }

    public void applyRankedMmr(Integer mmr, int completedRankedGames) {
        this.mmr = normalizeMmr(mmr);
    }

    public Integer getBaseMmr() {
        return baseMmr;
    }

    public void setBaseMmr(Integer baseMmr) {
        this.baseMmr = baseMmr;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getChatLeftAt() {
        return chatLeftAt;
    }

    public void setChatLeftAt(OffsetDateTime chatLeftAt) {
        this.chatLeftAt = chatLeftAt;
    }

    public String getChatLeftReason() {
        return chatLeftReason;
    }

    public void setChatLeftReason(String chatLeftReason) {
        this.chatLeftReason = chatLeftReason;
    }

    public OffsetDateTime getChatRejoinedAt() {
        return chatRejoinedAt;
    }

    public void setChatRejoinedAt(OffsetDateTime chatRejoinedAt) {
        this.chatRejoinedAt = chatRejoinedAt;
    }

    public String getTierChangeAcknowledgedTier() {
        return tierChangeAcknowledgedTier;
    }

    public void setTierChangeAcknowledgedTier(String tierChangeAcknowledgedTier) {
        this.tierChangeAcknowledgedTier = tierChangeAcknowledgedTier;
    }

    public OffsetDateTime getTierChangeAcknowledgedAt() {
        return tierChangeAcknowledgedAt;
    }

    public void setTierChangeAcknowledgedAt(OffsetDateTime tierChangeAcknowledgedAt) {
        this.tierChangeAcknowledgedAt = tierChangeAcknowledgedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getAnonymizedAt() {
        return anonymizedAt;
    }

    public void setAnonymizedAt(OffsetDateTime anonymizedAt) {
        this.anonymizedAt = anonymizedAt;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }

    public PlayerLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(PlayerLifecycleStatus lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public OffsetDateTime getIdentityRetainedUntil() {
        return identityRetainedUntil;
    }

    public void setIdentityRetainedUntil(OffsetDateTime identityRetainedUntil) {
        this.identityRetainedUntil = identityRetainedUntil;
    }

    public String getRetentionSubjectHash() {
        return retentionSubjectHash;
    }

    public void setRetentionSubjectHash(String retentionSubjectHash) {
        this.retentionSubjectHash = retentionSubjectHash;
    }

    public OffsetDateTime getLastTierRecalculatedAt() {
        return lastTierRecalculatedAt;
    }

    public void setLastTierRecalculatedAt(OffsetDateTime lastTierRecalculatedAt) {
        this.lastTierRecalculatedAt = lastTierRecalculatedAt;
    }

    public OffsetDateTime getLastTierSnapshotAt() {
        return lastTierSnapshotAt;
    }

    public void setLastTierSnapshotAt(OffsetDateTime lastTierSnapshotAt) {
        this.lastTierSnapshotAt = lastTierSnapshotAt;
    }

    public Integer getLastTierSnapshotMmr() {
        return lastTierSnapshotMmr;
    }

    public void setLastTierSnapshotMmr(Integer lastTierSnapshotMmr) {
        this.lastTierSnapshotMmr = lastTierSnapshotMmr;
    }

    @PrePersist
    @PreUpdate
    void syncTierWithMmr() {
        this.race = PlayerRacePolicy.normalizeCapabilityOrDefault(this.race, "P");
        this.mmr = normalizeMmr(this.mmr);
        if (this.tier == null || this.tier.isBlank()) {
            this.tier = PlayerTierPolicy.resolveTier(this.mmr);
        }
        if (this.anonymizedAt != null) {
            this.active = false;
            this.lifecycleStatus = PlayerLifecycleStatus.ANONYMIZED;
            this.identityRetainedUntil = null;
            this.retentionSubjectHash = null;
        } else if (this.lifecycleStatus == PlayerLifecycleStatus.ANONYMIZED) {
            this.active = false;
            this.anonymizedAt = OffsetDateTime.now();
            this.identityRetainedUntil = null;
            this.retentionSubjectHash = null;
        } else if (this.lifecycleStatus == PlayerLifecycleStatus.WITHDRAWN) {
            this.active = false;
            this.retentionSubjectHash = null;
        } else if (this.lifecycleStatus == PlayerLifecycleStatus.INACTIVE) {
            this.active = false;
        } else if (this.active) {
            this.lifecycleStatus = PlayerLifecycleStatus.ACTIVE;
            this.identityRetainedUntil = null;
            this.retentionSubjectHash = null;
        } else if (this.lifecycleStatus == null || this.lifecycleStatus == PlayerLifecycleStatus.ACTIVE) {
            this.lifecycleStatus = PlayerLifecycleStatus.INACTIVE;
        }
        recordHighestAchievedTier(this.tier);
    }

    private void recordHighestAchievedTier(String tier) {
        String nextHighestTier = PlayerTierPolicy.resolveHigherRankedTier(
            this.highestAchievedTier,
            tier
        );
        this.highestAchievedTier = nextHighestTier.isEmpty() ? null : nextHighestTier;
    }

    private int normalizeMmr(Integer mmr) {
        return Math.max(0, mmr == null ? 0 : mmr);
    }
}
