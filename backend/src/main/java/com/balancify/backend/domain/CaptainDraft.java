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
import java.time.OffsetDateTime;

@Entity
@Table(name = "captain_drafts")
public class CaptainDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 20)
    private String status = "DRAFTING";

    @Column(nullable = false)
    private Integer setsPerRound = 4;

    @Column(length = 20)
    private String currentTurnTeam = "HOME";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_captain_player_id", nullable = false)
    private Player homeCaptain;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_captain_player_id", nullable = false)
    private Player awayCaptain;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PrePersist
    @PreUpdate
    private void updateTimestamp() {
        this.updatedAt = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSetsPerRound() {
        return setsPerRound;
    }

    public void setSetsPerRound(Integer setsPerRound) {
        this.setsPerRound = setsPerRound;
    }

    public String getCurrentTurnTeam() {
        return currentTurnTeam;
    }

    public void setCurrentTurnTeam(String currentTurnTeam) {
        this.currentTurnTeam = currentTurnTeam;
    }

    public Player getHomeCaptain() {
        return homeCaptain;
    }

    public void setHomeCaptain(Player homeCaptain) {
        this.homeCaptain = homeCaptain;
    }

    public Player getAwayCaptain() {
        return awayCaptain;
    }

    public void setAwayCaptain(Player awayCaptain) {
        this.awayCaptain = awayCaptain;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
