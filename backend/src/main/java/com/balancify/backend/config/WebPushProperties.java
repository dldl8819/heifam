package com.balancify.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VAPID keys for Web Push, as base64url: the uncompressed P-256 public key and the 32-byte private
 * key (npx web-push generate-vapid-keys prints both). Without them notifications stay in the app.
 */
@Component
@ConfigurationProperties(prefix = "balancify.push")
public class WebPushProperties {

    private String vapidPublicKey = "";
    private String vapidPrivateKey = "";
    // A contact for push services, mailto: or https:.
    private String subject = "";

    public String getVapidPublicKey() {
        return vapidPublicKey;
    }

    public void setVapidPublicKey(String vapidPublicKey) {
        this.vapidPublicKey = vapidPublicKey == null ? "" : vapidPublicKey.trim();
    }

    public String getVapidPrivateKey() {
        return vapidPrivateKey;
    }

    public void setVapidPrivateKey(String vapidPrivateKey) {
        this.vapidPrivateKey = vapidPrivateKey == null ? "" : vapidPrivateKey.trim();
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject == null ? "" : subject.trim();
    }
}
