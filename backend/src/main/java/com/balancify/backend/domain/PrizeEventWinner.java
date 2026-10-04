package com.balancify.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A confirmed winner. Email and nickname are cleared when the winner deletes their account. */
@Entity
@Table(name = "prize_event_winners")
public class PrizeEventWinner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private int place;

    @Column(name = "normalized_email", length = 320)
    private String normalizedEmail;

    @Column(length = 100)
    private String nickname;

    @Column(nullable = false)
    private int points;

    @Column(length = 100)
    private String prize;

    @Column(nullable = false)
    private long amount;

    @Column(name = "ledger_expense_id")
    private Long ledgerExpenseId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public int getPlace() {
        return place;
    }

    public void setPlace(int place) {
        this.place = place;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public void setNormalizedEmail(String normalizedEmail) {
        this.normalizedEmail = normalizedEmail;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public String getPrize() {
        return prize;
    }

    public void setPrize(String prize) {
        this.prize = prize;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public Long getLedgerExpenseId() {
        return ledgerExpenseId;
    }

    public void setLedgerExpenseId(Long ledgerExpenseId) {
        this.ledgerExpenseId = ledgerExpenseId;
    }
}
