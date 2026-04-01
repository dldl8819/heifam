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
import java.util.Locale;

@Entity
@Table(name = "match_participants")
public class MatchParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(nullable = false, length = 20)
    private String team;

    @Column(nullable = false, length = 2)
    private String race = "P";

    @Column
    private Integer mmrBefore;

    @Column
    private Integer mmrAfter;

    @Column(nullable = false)
    private Integer mmrDelta = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getRace() {
        return race;
    }

    public void setRace(String race) {
        this.race = normalizeRace(race);
    }

    public Integer getMmrBefore() {
        return mmrBefore;
    }

    public void setMmrBefore(Integer mmrBefore) {
        this.mmrBefore = mmrBefore;
    }

    public Integer getMmrAfter() {
        return mmrAfter;
    }

    public void setMmrAfter(Integer mmrAfter) {
        this.mmrAfter = mmrAfter;
    }

    public Integer getMmrDelta() {
        return mmrDelta;
    }

    public void setMmrDelta(Integer mmrDelta) {
        this.mmrDelta = mmrDelta;
    }

    @PrePersist
    @PreUpdate
    private void normalizeParticipantRace() {
        this.race = normalizeRace(this.race);
    }

    private String normalizeRace(String value) {
        if (value == null || value.isBlank()) {
            return "P";
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "P", "T", "Z", "PT", "PZ", "TZ", "R" -> normalized;
            default -> "P";
        };
    }
}
