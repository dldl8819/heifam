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
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "captain_draft_entries",
    uniqueConstraints = @UniqueConstraint(columnNames = { "draft_id", "round_number", "set_number" })
)
public class CaptainDraftEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    private CaptainDraft draft;

    @Column(nullable = false)
    private Integer roundNumber;

    @Column(nullable = false, length = 20)
    private String roundCode;

    @Column(nullable = false)
    private Integer setNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_player_id")
    private Player homePlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_player_id")
    private Player awayPlayer;

    @Column(nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PrePersist
    @PreUpdate
    private void updateTimestamp() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CaptainDraft getDraft() {
        return draft;
    }

    public void setDraft(CaptainDraft draft) {
        this.draft = draft;
    }

    public Integer getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(Integer roundNumber) {
        this.roundNumber = roundNumber;
    }

    public String getRoundCode() {
        return roundCode;
    }

    public void setRoundCode(String roundCode) {
        this.roundCode = roundCode;
    }

    public Integer getSetNumber() {
        return setNumber;
    }

    public void setSetNumber(Integer setNumber) {
        this.setNumber = setNumber;
    }

    public Player getHomePlayer() {
        return homePlayer;
    }

    public void setHomePlayer(Player homePlayer) {
        this.homePlayer = homePlayer;
    }

    public Player getAwayPlayer() {
        return awayPlayer;
    }

    public void setAwayPlayer(Player awayPlayer) {
        this.awayPlayer = awayPlayer;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
