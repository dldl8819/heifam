package com.balancify.backend.domain;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/** Two tournament teams playing up to three games; each game is a match with this series' id. */
@Entity
@Table(name = "match_series")
public class MatchSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private TeamTournament tournament;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchSeriesRound round;

    @Column(name = "bracket_slot", nullable = false)
    private int bracketSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private TournamentTeam homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private TournamentTeam awayTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchSeriesFormat format;

    // Comma-separated race composition of each planned game, e.g. "PPP,PPT,PPZ".
    @Column(name = "game_compositions", nullable = false, length = 40)
    private String gameCompositions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchSeriesStatus status = MatchSeriesStatus.IN_PROGRESS;

    @Column(name = "home_wins", nullable = false)
    private int homeWins;

    @Column(name = "away_wins", nullable = false)
    private int awayWins;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_team_id")
    private TournamentTeam winnerTeam;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public TeamTournament getTournament() {
        return tournament;
    }

    public void setTournament(TeamTournament tournament) {
        this.tournament = tournament;
    }

    public MatchSeriesRound getRound() {
        return round;
    }

    public void setRound(MatchSeriesRound round) {
        this.round = round;
    }

    public int getBracketSlot() {
        return bracketSlot;
    }

    public void setBracketSlot(int bracketSlot) {
        this.bracketSlot = bracketSlot;
    }

    public TournamentTeam getHomeTeam() {
        return homeTeam;
    }

    public void setHomeTeam(TournamentTeam homeTeam) {
        this.homeTeam = homeTeam;
    }

    public TournamentTeam getAwayTeam() {
        return awayTeam;
    }

    public void setAwayTeam(TournamentTeam awayTeam) {
        this.awayTeam = awayTeam;
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

    public MatchSeriesStatus getStatus() {
        return status;
    }

    public void setStatus(MatchSeriesStatus status) {
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

    public TournamentTeam getWinnerTeam() {
        return winnerTeam;
    }

    public void setWinnerTeam(TournamentTeam winnerTeam) {
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
