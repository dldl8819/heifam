package com.balancify.backend.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One set of a round of the regular draft: a team game of team size a side (3:3 or 2:2; sets
 * written before V79 were one against one), with its players per side and its result.
 */
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

    @Column(nullable = false)
    private Integer teamSize = 3;

    // A note on the tiers the set asks for, such as 상위 3 or 핫티어 1; not enforced.
    @Column(length = 30)
    private String requiredTier;

    // The set of the extra round played when the regular rounds end level (에이스 결정전, V82).
    @Column(nullable = false)
    private boolean aceDecider;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("team asc, slot asc")
    private List<CaptainDraftEntryPlayer> players = new ArrayList<>();

    @Column(length = 10)
    private String winnerTeam;

    // Who put the result in, and when: the email stays on the server, the nickname is shown.
    @Column(length = 320)
    private String resultRecordedByEmail;

    @Column(length = 100)
    private String resultRecordedByNickname;

    @Column
    private OffsetDateTime resultRecordedAt;

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

    public Integer getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(Integer teamSize) {
        this.teamSize = teamSize;
    }

    public String getRequiredTier() {
        return requiredTier;
    }

    public void setRequiredTier(String requiredTier) {
        this.requiredTier = requiredTier;
    }

    public boolean isAceDecider() {
        return aceDecider;
    }

    public void setAceDecider(boolean aceDecider) {
        this.aceDecider = aceDecider;
    }

    public List<CaptainDraftEntryPlayer> getPlayers() {
        return players;
    }

    /** The players of one side, slot by slot: index 0 is slot 1, null where the slot is empty. */
    public List<Player> sidePlayers(String team) {
        int size = teamSize == null || teamSize < 1 ? 1 : teamSize;
        List<Player> side = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            side.add(null);
        }
        for (CaptainDraftEntryPlayer slotted : players) {
            if (team.equals(slotted.getTeam()) && slotted.getSlot() != null
                && slotted.getSlot() >= 1 && slotted.getSlot() <= size) {
                side.set(slotted.getSlot() - 1, slotted.getPlayer());
            }
        }
        return side;
    }

    /**
     * Sets one side to these players, slot by slot (index 0 is slot 1, null leaves it empty). A slot
     * that keeps a row has its player changed in place rather than being removed and inserted
     * again, so the flush never meets the (entry, team, slot) unique key twice.
     */
    public void setSidePlayers(String team, List<Player> side) {
        for (int index = 0; index < side.size(); index++) {
            int slot = index + 1;
            Player player = side.get(index);
            CaptainDraftEntryPlayer existing = players
                .stream()
                .filter(candidate -> team.equals(candidate.getTeam()) && Integer.valueOf(slot).equals(candidate.getSlot()))
                .findFirst()
                .orElse(null);
            if (player == null) {
                if (existing != null) {
                    players.remove(existing);
                }
            } else if (existing != null) {
                existing.setPlayer(player);
            } else {
                CaptainDraftEntryPlayer added = new CaptainDraftEntryPlayer();
                added.setEntry(this);
                added.setTeam(team);
                added.setSlot(slot);
                added.setPlayer(player);
                players.add(added);
            }
        }
        players.removeIf(candidate -> team.equals(candidate.getTeam())
            && (candidate.getSlot() == null || candidate.getSlot() > side.size()));
    }

    public String getWinnerTeam() {
        return winnerTeam;
    }

    public void setWinnerTeam(String winnerTeam) {
        this.winnerTeam = winnerTeam;
    }

    public String getResultRecordedByEmail() {
        return resultRecordedByEmail;
    }

    public String getResultRecordedByNickname() {
        return resultRecordedByNickname;
    }

    public OffsetDateTime getResultRecordedAt() {
        return resultRecordedAt;
    }

    /** Names who put the result in and when; all null when the result is taken out. */
    public void setResultRecordedBy(String email, String nickname, OffsetDateTime recordedAt) {
        this.resultRecordedByEmail = email;
        this.resultRecordedByNickname = nickname;
        this.resultRecordedAt = recordedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
