package com.balancify.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessPersonMergeRepositoryTest {

    @Test
    void readsWhatAPointWasForFromItsReference() {
        assertThat(AccessPersonMergeRepository.thingOf("2026-10-10")).isEqualTo("2026-10-10");
        assertThat(AccessPersonMergeRepository.thingOf("notice:5:r2")).isEqualTo("notice:5:r2");
        // A prediction's sequence and a key moved in from another account do not change what it was for.
        assertThat(AccessPersonMergeRepository.thingOf("prediction:12#3")).isEqualTo("prediction:12");
        assertThat(AccessPersonMergeRepository.thingOf("prediction:12#1~555")).isEqualTo("prediction:12");
        assertThat(AccessPersonMergeRepository.thingOf("notice-comment:9~77")).isEqualTo("notice-comment:9");
        assertThat(AccessPersonMergeRepository.thingOf(null)).isEmpty();
    }
}
