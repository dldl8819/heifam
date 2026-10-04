package com.balancify.backend.api.notification.dto;

/** Whether Web Push is on, and the VAPID public key browsers subscribe with. */
public record PushConfigResponse(boolean enabled, String publicKey) {
}
