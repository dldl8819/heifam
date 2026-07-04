package com.balancify.backend.domain;

import java.io.Serializable;
import java.util.Objects;

public class PlayerRaceStatsId implements Serializable {

    private Long playerId;
    private String race;

    public PlayerRaceStatsId() {
    }

    public PlayerRaceStatsId(Long playerId, String race) {
        this.playerId = playerId;
        this.race = race;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public String getRace() {
        return race;
    }

    public void setRace(String race) {
        this.race = race;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PlayerRaceStatsId that)) {
            return false;
        }
        return Objects.equals(playerId, that.playerId)
            && Objects.equals(race, that.race);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, race);
    }
}
