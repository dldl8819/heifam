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
    // Liking someone else's comment on a notice, once per comment.
    private int noticeCommentLike = 1;
    private int noticeCommentLikeDailyCap = 10;
    // A player confirming the result of a match they played, once per match.
    private int matchConfirm = 1;
    private int matchConfirmDailyCap = 10;
    private int matchConfirmWindowHours = 48;
    // The free board and the video board: writing a post, and commenting on and liking someone
    // else's post, once per post. The anonymous board earns nothing, so no point row can tell who
    // wrote there.
    private int boardPost = 1;
    private int boardPostDailyCap = 3;
    private int boardComment = 1;
    private int boardCommentDailyCap = 10;
    private int boardLike = 1;
    private int boardLikeDailyCap = 10;

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

    public int getNoticeCommentLike() {
        return noticeCommentLike;
    }

    public void setNoticeCommentLike(int noticeCommentLike) {
        this.noticeCommentLike = Math.max(0, noticeCommentLike);
    }

    public int getNoticeCommentLikeDailyCap() {
        return noticeCommentLikeDailyCap;
    }

    public void setNoticeCommentLikeDailyCap(int noticeCommentLikeDailyCap) {
        this.noticeCommentLikeDailyCap = Math.max(0, noticeCommentLikeDailyCap);
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

    public int getBoardPost() {
        return boardPost;
    }

    public void setBoardPost(int boardPost) {
        this.boardPost = Math.max(0, boardPost);
    }

    public int getBoardPostDailyCap() {
        return boardPostDailyCap;
    }

    public void setBoardPostDailyCap(int boardPostDailyCap) {
        this.boardPostDailyCap = Math.max(0, boardPostDailyCap);
    }

    public int getBoardComment() {
        return boardComment;
    }

    public void setBoardComment(int boardComment) {
        this.boardComment = Math.max(0, boardComment);
    }

    public int getBoardCommentDailyCap() {
        return boardCommentDailyCap;
    }

    public void setBoardCommentDailyCap(int boardCommentDailyCap) {
        this.boardCommentDailyCap = Math.max(0, boardCommentDailyCap);
    }

    public int getBoardLike() {
        return boardLike;
    }

    public void setBoardLike(int boardLike) {
        this.boardLike = Math.max(0, boardLike);
    }

    public int getBoardLikeDailyCap() {
        return boardLikeDailyCap;
    }

    public void setBoardLikeDailyCap(int boardLikeDailyCap) {
        this.boardLikeDailyCap = Math.max(0, boardLikeDailyCap);
    }
}
