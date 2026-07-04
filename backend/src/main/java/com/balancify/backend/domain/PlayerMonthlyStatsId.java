package com.balancify.backend.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class PlayerMonthlyStatsId implements Serializable {

    private Long playerId;
    private LocalDate statMonth;

    public PlayerMonthlyStatsId() {
    }

    public PlayerMonthlyStatsId(Long playerId, LocalDate statMonth) {
        this.playerId = playerId;
        this.statMonth = statMonth;
    }

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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PlayerMonthlyStatsId that)) {
            return false;
        }
        return Objects.equals(playerId, that.playerId)
            && Objects.equals(statMonth, that.statMonth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, statMonth);
    }
}
