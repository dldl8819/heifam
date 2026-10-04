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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(nullable = false)
    private OffsetDateTime playedAt = OffsetDateTime.now();

    @Column(length = 20)
    private String winningTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchSource source = MatchSource.BALANCED;

    @Column(name = "team_size", nullable = false)
    private Integer teamSize = 3;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "participant_signature", length = 255)
    private String participantSignature;

    @Column(name = "team_signature", length = 255)
    private String teamSignature;

    @Column(length = 255)
    private String note;

    @Column(name = "race_composition", length = 10)
    private String raceComposition;

    @Column(name = "races_recorded", nullable = false)
    private boolean racesRecorded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column
    private OffsetDateTime resultRecordedAt;

    @Column(length = 320)
    private String resultRecordedByEmail;

    @Column(length = 100)
    private String resultRecordedByNickname;

    // Set for a team tournament game: the series it belongs to and its place (1-3) in it.
    @Column(name = "series_id")
    private Long seriesId;

    @Column(name = "series_game_number")
    private Integer seriesGameNumber;

    // Set for a game of a series started after a multi-balance; series_game_number is its place in it.
    @Column(name = "balance_series_id")
    private Long balanceSeriesId;

    // Set when an admin closes predictions early; otherwise they close a few minutes after creation.
    @Column(name = "predictions_closed_at")
    private OffsetDateTime predictionsClosedAt;

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

    public OffsetDateTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(OffsetDateTime playedAt) {
        this.playedAt = playedAt;
    }

    public String getWinningTeam() {
        return winningTeam;
    }

    public void setWinningTeam(String winningTeam) {
        this.winningTeam = winningTeam;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public MatchSource getSource() {
        return source;
    }

    public void setSource(MatchSource source) {
        this.source = source;
    }

    public Integer getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(Integer teamSize) {
        this.teamSize = teamSize;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getParticipantSignature() {
        return participantSignature;
    }

    public void setParticipantSignature(String participantSignature) {
        this.participantSignature = participantSignature;
    }

    public String getTeamSignature() {
        return teamSignature;
    }

    public void setTeamSignature(String teamSignature) {
        this.teamSignature = teamSignature;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getRaceComposition() {
        return raceComposition;
    }

    public void setRaceComposition(String raceComposition) {
        this.raceComposition = raceComposition;
    }

    public boolean isRacesRecorded() {
        return racesRecorded;
    }

    public void setRacesRecorded(boolean racesRecorded) {
        this.racesRecorded = racesRecorded;
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

    public OffsetDateTime getResultRecordedAt() {
        return resultRecordedAt;
    }

    public void setResultRecordedAt(OffsetDateTime resultRecordedAt) {
        this.resultRecordedAt = resultRecordedAt;
    }

    public String getResultRecordedByEmail() {
        return resultRecordedByEmail;
    }

    public void setResultRecordedByEmail(String resultRecordedByEmail) {
        this.resultRecordedByEmail = resultRecordedByEmail;
    }

    public String getResultRecordedByNickname() {
        return resultRecordedByNickname;
    }

    public void setResultRecordedByNickname(String resultRecordedByNickname) {
        this.resultRecordedByNickname = resultRecordedByNickname;
    }

    public Long getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(Long seriesId) {
        this.seriesId = seriesId;
    }

    public Integer getSeriesGameNumber() {
        return seriesGameNumber;
    }

    public void setSeriesGameNumber(Integer seriesGameNumber) {
        this.seriesGameNumber = seriesGameNumber;
    }

    public Long getBalanceSeriesId() {
        return balanceSeriesId;
    }

    public void setBalanceSeriesId(Long balanceSeriesId) {
        this.balanceSeriesId = balanceSeriesId;
    }

    public OffsetDateTime getPredictionsClosedAt() {
        return predictionsClosedAt;
    }

    public void setPredictionsClosedAt(OffsetDateTime predictionsClosedAt) {
        this.predictionsClosedAt = predictionsClosedAt;
    }

    @PrePersist
    private void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = MatchStatus.DRAFT;
        }
        if (source == null) {
            source = MatchSource.BALANCED;
        }
        if (teamSize == null || teamSize <= 0) {
            teamSize = 3;
        }
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = OffsetDateTime.now();
        if (status == null) {
            status = MatchStatus.DRAFT;
        }
        if (source == null) {
            source = MatchSource.BALANCED;
        }
        if (teamSize == null || teamSize <= 0) {
            teamSize = 3;
        }
    }
}
