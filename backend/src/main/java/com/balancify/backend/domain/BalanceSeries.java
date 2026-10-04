package com.balancify.backend.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The two teams of a multi-balance match playing up to three games; each game is a match with this series' id. */
@Entity
@Table(name = "balance_series")
public class BalanceSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "team_size", nullable = false)
    private int teamSize;

    // The match of its multi-balance (1, 2, ...); its teams are 2n-1 (HOME) and 2n (AWAY).
    @Column(name = "match_number")
    private Integer matchNumber;

    // HOME players first, then AWAY, team_size each.
    @ElementCollection
    @CollectionTable(name = "balance_series_players", joinColumns = @JoinColumn(name = "series_id"))
    @OrderColumn(name = "slot")
    @Column(name = "player_id", nullable = false)
    private List<Long> playerIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchSeriesFormat format;

    // Comma-separated race composition of each planned game, e.g. "PPP,PPT,PPZ".
    @Column(name = "game_compositions", nullable = false, length = 40)
    private String gameCompositions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BalanceSeriesStatus status = BalanceSeriesStatus.IN_PROGRESS;

    @Column(name = "home_wins", nullable = false)
    private int homeWins;

    @Column(name = "away_wins", nullable = false)
    private int awayWins;

    // HOME or AWAY once the series is decided.
    @Column(name = "winner_team", length = 10)
    private String winnerTeam;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(int teamSize) {
        this.teamSize = teamSize;
    }

    public Integer getMatchNumber() {
        return matchNumber;
    }

    public void setMatchNumber(Integer matchNumber) {
        this.matchNumber = matchNumber;
    }

    public List<Long> getPlayerIds() {
        return playerIds;
    }

    public void setPlayerIds(List<Long> playerIds) {
        this.playerIds = playerIds;
    }

    public List<Long> homePlayerIds() {
        return List.copyOf(playerIds.subList(0, Math.min(teamSize, playerIds.size())));
    }

    public List<Long> awayPlayerIds() {
        return List.copyOf(playerIds.subList(Math.min(teamSize, playerIds.size()), playerIds.size()));
    }

    public MatchSeriesFormat getFormat() {
        return format;
    }

    public void setFormat(MatchSeriesFormat format) {
        this.format = format;
    }

    public String getGameCompositions() {
        return gameCompositions;
    }

    public void setGameCompositions(String gameCompositions) {
        this.gameCompositions = gameCompositions;
    }

    public List<String> plannedCompositions() {
        return gameCompositions == null || gameCompositions.isBlank()
            ? List.of()
            : Arrays.stream(gameCompositions.split(",")).map(String::trim).toList();
    }

    public BalanceSeriesStatus getStatus() {
        return status;
    }

    public void setStatus(BalanceSeriesStatus status) {
        this.status = status;
    }

    public int getHomeWins() {
        return homeWins;
    }

    public void setHomeWins(int homeWins) {
        this.homeWins = homeWins;
    }

    public int getAwayWins() {
        return awayWins;
    }

    public void setAwayWins(int awayWins) {
        this.awayWins = awayWins;
    }

    public String getWinnerTeam() {
        return winnerTeam;
    }

    public void setWinnerTeam(String winnerTeam) {
        this.winnerTeam = winnerTeam;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
