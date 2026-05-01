package com.balancify.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import com.balancify.backend.service.PlayerRacePolicy;
import java.time.OffsetDateTime;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 3)
    private String race = "P";

    @Column(length = 20)
    private String tier = PlayerTierPolicy.resolveTier(1000);

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

    @Column(name = "last_dormancy_mmr_decay_at")
    private OffsetDateTime lastDormancyMmrDecayAt;

    @Column(name = "last_tier_recalculated_at")
    private OffsetDateTime lastTierRecalculatedAt;

    @Column(name = "last_tier_snapshot_at")
    private OffsetDateTime lastTierSnapshotAt;

    @Column(name = "last_tier_snapshot_mmr")
    private Integer lastTierSnapshotMmr;

    @Column(name = "dormant_since")
    private OffsetDateTime dormantSince;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(name = "return_boost_games_remaining", nullable = false)
    private Integer returnBoostGamesRemaining = 0;

    @Column(name = "return_boost_multiplier", nullable = false)
    private Double returnBoostMultiplier = 1.0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
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
    }

    public Integer getMmr() {
        return mmr;
    }

    public void setMmr(Integer mmr) {
        this.mmr = mmr;
    }

    public void applyRankedMmr(Integer mmr, int completedRankedGames) {
        this.mmr = mmr;
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

    public OffsetDateTime getLastDormancyMmrDecayAt() {
        return lastDormancyMmrDecayAt;
    }

    public void setLastDormancyMmrDecayAt(OffsetDateTime lastDormancyMmrDecayAt) {
        this.lastDormancyMmrDecayAt = lastDormancyMmrDecayAt;
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

    public OffsetDateTime getDormantSince() {
        return dormantSince;
    }

    public void setDormantSince(OffsetDateTime dormantSince) {
        this.dormantSince = dormantSince;
    }

    public OffsetDateTime getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(OffsetDateTime returnedAt) {
        this.returnedAt = returnedAt;
    }

    public Integer getReturnBoostGamesRemaining() {
        return returnBoostGamesRemaining;
    }

    public void setReturnBoostGamesRemaining(Integer returnBoostGamesRemaining) {
        this.returnBoostGamesRemaining = returnBoostGamesRemaining == null ? 0 : Math.max(0, returnBoostGamesRemaining);
    }

    public Double getReturnBoostMultiplier() {
        return returnBoostMultiplier;
    }

    public void setReturnBoostMultiplier(Double returnBoostMultiplier) {
        this.returnBoostMultiplier = returnBoostMultiplier == null ? 1.0 : Math.max(1.0, returnBoostMultiplier);
    }

    @PrePersist
    @PreUpdate
    private void syncTierWithMmr() {
        this.race = PlayerRacePolicy.normalizeCapabilityOrDefault(this.race, "P");
        this.returnBoostGamesRemaining = this.returnBoostGamesRemaining == null
            ? 0
            : Math.max(0, this.returnBoostGamesRemaining);
        this.returnBoostMultiplier = this.returnBoostMultiplier == null
            ? 1.0
            : Math.max(1.0, this.returnBoostMultiplier);
        if (this.tier == null || this.tier.isBlank()) {
            this.tier = PlayerTierPolicy.resolveTier(this.mmr);
        }
    }
}
