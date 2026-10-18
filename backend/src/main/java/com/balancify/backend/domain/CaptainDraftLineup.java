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
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

/**
 * A team's line-up for one stage of a draft run by its captains, handed in once by that team's
 * captain: MAIN for every regular round, ACE for the ace decider. The players themselves are in the
 * sets.
 */
@Entity
@Table(
    name = "captain_draft_lineups",
    uniqueConstraints = @UniqueConstraint(columnNames = { "draft_id", "stage", "team" })
)
public class CaptainDraftLineup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    private CaptainDraft draft;

    @Column(nullable = false, length = 10)
    private String stage;

    @Column(nullable = false, length = 10)
    private String team;

    @Column(nullable = false)
    private OffsetDateTime submittedAt;

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

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
}
