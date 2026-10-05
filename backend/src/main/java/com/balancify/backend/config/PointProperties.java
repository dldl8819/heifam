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
    private int predictionHit = 1;
    private int predictionHitDailyCap = 10;
    // Reading, liking and commenting on a notice each earn this once per notice.
    private int noticeAction = 1;
    // A player confirming the result of a match they played, once per match.
    private int matchConfirm = 1;
    private int matchConfirmDailyCap = 10;
    private int matchConfirmWindowHours = 48;

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

    public int getPredictionHit() {
        return predictionHit;
    }

    public void setPredictionHit(int predictionHit) {
        this.predictionHit = Math.max(0, predictionHit);
    }

    public int getPredictionHitDailyCap() {
        return predictionHitDailyCap;
    }

    public void setPredictionHitDailyCap(int predictionHitDailyCap) {
        this.predictionHitDailyCap = Math.max(0, predictionHitDailyCap);
    }

    public int getNoticeAction() {
        return noticeAction;
    }

    public void setNoticeAction(int noticeAction) {
        this.noticeAction = Math.max(0, noticeAction);
    }

    public int getMatchConfirm() {
        return matchConfirm;
    }

    public void setMatchConfirm(int matchConfirm) {
        this.matchConfirm = Math.max(0, matchConfirm);
    }

    public int getMatchConfirmDailyCap() {
        return matchConfirmDailyCap;
    }

    public void setMatchConfirmDailyCap(int matchConfirmDailyCap) {
        this.matchConfirmDailyCap = Math.max(0, matchConfirmDailyCap);
    }

    public int getMatchConfirmWindowHours() {
        return matchConfirmWindowHours;
    }

    public void setMatchConfirmWindowHours(int matchConfirmWindowHours) {
        this.matchConfirmWindowHours = Math.max(1, matchConfirmWindowHours);
    }
}
