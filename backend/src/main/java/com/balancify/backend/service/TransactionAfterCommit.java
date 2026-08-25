package com.balancify.backend.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class TransactionAfterCommit {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionAfterCommit.class);

    // Single-threaded so back-to-back post-commit jobs (e.g. group stats rebuilds for the same
    // group) are naturally serialized instead of racing with each other.
    private static final ExecutorService AFTER_COMMIT_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "after-commit-async");
        thread.setDaemon(true);
        return thread;
    });

    private TransactionAfterCommit() {
    }

    static void runNowAndAfterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        action.run();
        if (!TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * Runs {@code action} on a background thread strictly after the current transaction commits,
     * so the caller can return its response without waiting for it. A failure in {@code action}
     * is logged, not rethrown — the triggering transaction has already committed by the time this
     * runs, so there is nothing left for the caller to roll back or fail.
     */
    static void runAfterCommitAsync(Runnable action) {
        if (action == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.isActualTransactionActive()) {
            AFTER_COMMIT_EXECUTOR.execute(() -> runLogged(action));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                AFTER_COMMIT_EXECUTOR.execute(() -> runLogged(action));
            }
        });
    }

    private static void runLogged(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOGGER.error("Post-commit async action failed", exception);
        }
    }
}
