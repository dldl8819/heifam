package com.balancify.backend.api.notification.dto;

/** A browser's PushSubscription as its toJSON() gives it; keys is left out when unsubscribing. */
public record PushSubscriptionRequest(String endpoint, Keys keys) {

    public record Keys(String p256dh, String auth) {
    }
}
