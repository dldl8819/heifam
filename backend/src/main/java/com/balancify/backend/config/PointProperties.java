package com.balancify.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "balancify.points")
public class PointProperties {

    // Points are tried out by admins first; until this is on, members neither earn nor see them.
    private boolean membersEnabled = false;
    private int dailyLogin = 1;
    private int matchResult = 1;
    private int matchResultDailyCap = 10;

    public boolean isMembersEnabled() {
        return membersEnabled;
    }

    public void setMembersEnabled(boolean membersEnabled) {
        this.membersEnabled = membersEnabled;
    }

    public int getDailyLogin() {
        return dailyLogin;
    }

    public void setDailyLogin(int dailyLogin) {
        this.dailyLogin = Math.max(0, dailyLogin);
    }

    public int getMatchResult() {
        return matchResult;
    }

    public void setMatchResult(int matchResult) {
        this.matchResult = Math.max(0, matchResult);
    }

    public int getMatchResultDailyCap() {
        return matchResultDailyCap;
    }

    public void setMatchResultDailyCap(int matchResultDailyCap) {
        this.matchResultDailyCap = Math.max(0, matchResultDailyCap);
    }
}
