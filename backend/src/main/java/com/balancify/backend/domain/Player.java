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

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

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
        this.tier = PlayerTierPolicy.resolveTier(mmr);
    }

    public void applyRankedMmr(Integer mmr, int completedRankedGames) {
        this.mmr = mmr;
        this.tier = PlayerTierPolicy.resolveTierForRankedMatch(this.tier, mmr, completedRankedGames);
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    @PreUpdate
    private void syncTierWithMmr() {
        this.race = PlayerRacePolicy.normalizeCapabilityOrDefault(this.race, "P");
        if (this.tier == null || this.tier.isBlank()) {
            this.tier = PlayerTierPolicy.resolveTier(this.mmr);
        }
    }
}
