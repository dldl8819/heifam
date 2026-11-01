package com.balancify.backend.domain;

/**
 * What ranks a prize event's period: points earned, matches played, or numbers counted outside the
 * site that the admin who starts the event enters (MANUAL, e.g. chat messages from a chat app).
 */
public enum PrizeEventMetric {
    POINTS,
    GAMES,
    MANUAL
}
