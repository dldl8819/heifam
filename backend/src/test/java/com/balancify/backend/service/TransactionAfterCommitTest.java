package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TransactionAfterCommitTest {

    @Test
    void runAfterCommitAsyncRunsActionOnABackgroundThreadWhenNoTransactionIsActive() throws InterruptedException {
        Thread callingThread = Thread.currentThread();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger runCount = new AtomicInteger(0);
        AtomicInteger ranOnDifferentThread = new AtomicInteger(0);

        TransactionAfterCommit.runAfterCommitAsync(() -> {
            runCount.incrementAndGet();
            if (Thread.currentThread() != callingThread) {
                ranOnDifferentThread.incrementAndGet();
            }
            latch.countDown();
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(runCount.get()).isEqualTo(1);
        assertThat(ranOnDifferentThread.get()).isEqualTo(1);
    }

    @Test
    void runAfterCommitAsyncDoesNotPropagateActionFailureToCaller() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        assertThatCode(() ->
            TransactionAfterCommit.runAfterCommitAsync(() -> {
                latch.countDown();
                throw new IllegalStateException("boom");
            })
        ).doesNotThrowAnyException();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void runAfterCommitAsyncDoesNothingForNullAction() {
        assertThatCode(() -> TransactionAfterCommit.runAfterCommitAsync(null)).doesNotThrowAnyException();
    }
}
