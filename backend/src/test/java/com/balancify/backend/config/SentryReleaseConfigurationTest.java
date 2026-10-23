package com.balancify.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.SentryOptions;
import org.junit.jupiter.api.Test;

class SentryReleaseConfigurationTest {

    @Test
    void namesTheDeployedCommitUnlessAReleaseIsSetOnPurpose() {
        SentryOptions deployed = new SentryOptions();
        SentryReleaseConfiguration.applyRelease(deployed, " 0123abc ");
        assertThat(deployed.getRelease()).isEqualTo("0123abc");

        SentryOptions chosen = new SentryOptions();
        chosen.setRelease("YOUR_RELEASE");
        SentryReleaseConfiguration.applyRelease(chosen, "0123abc");
        assertThat(chosen.getRelease()).isEqualTo("YOUR_RELEASE");

        SentryOptions local = new SentryOptions();
        SentryReleaseConfiguration.applyRelease(local, null);
        SentryReleaseConfiguration.applyRelease(local, " ");
        assertThat(local.getRelease()).isNull();
    }
}
