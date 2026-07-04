package com.balancify.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@IdClass(PlayerMonthlyStatsId.class)
@Table(name = "player_monthly_stats")
public class PlayerMonthlyStats {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @Id
    @Column(name = "stat_month", nullable = false)
    private LocalDate statMonth;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private Integer wins = 0;

    @Column(nullable = false)
    private Integer losses = 0;

    @Column(nullable = false)
    private Integer games = 0;

    @Column(nullable = false, length = 10)
    private String last10 = "";

    @Column(name = "streak_symbol", nullable = false, length = 1)
    private String streakSymbol = "N";

    @Column(name = "streak_count", nullable = false)
    private Integer streakCount = 0;

    @Column(name = "mmr_delta", nullable = false)
    private Integer mmrDelta = 0;

    @Column(name = "last_played_at")
    private OffsetDateTime lastPlayedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public LocalDate getStatMonth() {
        return statMonth;
    }

    public void setStatMonth(LocalDate statMonth) {
        this.statMonth = statMonth;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Integer getWins() {
        return wins;
    }

    public void setWins(Integer wins) {
        this.wins = wins;
    }

    public Integer getLosses() {
        return losses;
    }

    public void setLosses(Integer losses) {
        this.losses = losses;
    }

    public Integer getGames() {
        return games;
    }

    public void setGames(Integer games) {
        this.games = games;
    }

    public String getLast10() {
        return last10;
    }

    public void setLast10(String last10) {
        this.last10 = last10;
    }

    public String getStreakSymbol() {
        return streakSymbol;
    }

    public void setStreakSymbol(String streakSymbol) {
        this.streakSymbol = streakSymbol;
    }

    public Integer getStreakCount() {
        return streakCount;
    }

    public void setStreakCount(Integer streakCount) {
        this.streakCount = streakCount;
    }

    public Integer getMmrDelta() {
        return mmrDelta;
    }

    public void setMmrDelta(Integer mmrDelta) {
        this.mmrDelta = mmrDelta;
    }

    public OffsetDateTime getLastPlayedAt() {
        return lastPlayedAt;
    }

    public void setLastPlayedAt(OffsetDateTime lastPlayedAt) {
        this.lastPlayedAt = lastPlayedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
