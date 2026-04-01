package com.balancify.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

    @Column
    private OffsetDateTime resultRecordedAt;

    @Column(length = 320)
    private String resultRecordedByEmail;

    @Column(length = 100)
    private String resultRecordedByNickname;

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
}
