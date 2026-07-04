package com.balancify.backend.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class PlayerMonthlyGameTypeStatsId implements Serializable {

    private Long playerId;
    private LocalDate statMonth;
    private String gameType;

    public PlayerMonthlyGameTypeStatsId() {
    }

    public PlayerMonthlyGameTypeStatsId(Long playerId, LocalDate statMonth, String gameType) {
        this.playerId = playerId;
        this.statMonth = statMonth;
        this.gameType = gameType;
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

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PlayerMonthlyGameTypeStatsId that)) {
            return false;
        }
        return Objects.equals(playerId, that.playerId)
            && Objects.equals(statMonth, that.statMonth)
            && Objects.equals(gameType, that.gameType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, statMonth, gameType);
    }
}
