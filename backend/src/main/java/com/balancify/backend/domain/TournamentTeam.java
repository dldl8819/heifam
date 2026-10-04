package com.balancify.backend.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournament_teams")
public class TournamentTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private TeamTournament tournament;

    @Column(name = "team_number", nullable = false)
    private int teamNumber;

    @Column(name = "final_rank")
    private Integer finalRank;

    @ElementCollection
    @CollectionTable(name = "tournament_team_members", joinColumns = @JoinColumn(name = "team_id"))
    @OrderColumn(name = "slot")
    @Column(name = "player_id", nullable = false)
    private List<Long> memberPlayerIds = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() {
        return id;
    }

    public TeamTournament getTournament() {
        return tournament;
    }

    public void setTournament(TeamTournament tournament) {
        this.tournament = tournament;
    }

    public int getTeamNumber() {
        return teamNumber;
    }

    public void setTeamNumber(int teamNumber) {
        this.teamNumber = teamNumber;
    }

    public Integer getFinalRank() {
        return finalRank;
    }

    public void setFinalRank(Integer finalRank) {
        this.finalRank = finalRank;
    }

    public List<Long> getMemberPlayerIds() {
        return memberPlayerIds;
    }

    public void setMemberPlayerIds(List<Long> memberPlayerIds) {
        this.memberPlayerIds = memberPlayerIds;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
