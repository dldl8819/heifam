package com.balancify.backend.domain;

import java.io.Serializable;
import java.util.Objects;

public class PlayerGameTypeStatsId implements Serializable {

    private Long playerId;
    private String gameType;

    public PlayerGameTypeStatsId() {
    }

    public PlayerGameTypeStatsId(Long playerId, String gameType) {
        this.playerId = playerId;
        this.gameType = gameType;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
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
        if (!(object instanceof PlayerGameTypeStatsId that)) {
            return false;
        }
        return Objects.equals(playerId, that.playerId)
            && Objects.equals(gameType, that.gameType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, gameType);
    }
}
