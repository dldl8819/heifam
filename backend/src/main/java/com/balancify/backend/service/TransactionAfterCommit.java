package com.balancify.backend.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    private static final ConcurrentMap<Object, CoalescedJob> COALESCED_JOBS = new ConcurrentHashMap<>();

    private TransactionAfterCommit() {
    }

    /** Conflation state for one coalesce key. All fields are guarded by the instance monitor. */
    private static final class CoalescedJob {
        private Runnable pending;
        private boolean running;
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
        runAfterCommitAsync(null, action);
    }

    /**
     * Same as {@link #runAfterCommitAsync(Runnable)}, but collapses redundant work.
     *
     * <p>Some post-commit jobs recompute a whole derived dataset rather than applying a delta, so
     * running one per triggering transaction is wasteful: importing a batch of matches queued one
     * full group stats rebuild per row, and every rebuild but the last was immediately superseded.
     *
     * <p>Actions sharing a {@code coalesceKey} are conflated: at most one runs at a time, and while
     * one runs at most one more is held pending. A request that arrives mid-run is never dropped —
     * it is picked up on the next pass — so the final run always observes every committed change.
     */
    static void runAfterCommitAsync(Object coalesceKey, Runnable action) {
        if (action == null) {
            return;
        }
        Runnable submit = coalesceKey == null
            ? () -> AFTER_COMMIT_EXECUTOR.execute(() -> runLogged(action))
            : () -> submitCoalesced(coalesceKey, action);

        if (!TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.isActualTransactionActive()) {
            submit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit.run();
            }
        });
    }

    private static void submitCoalesced(Object coalesceKey, Runnable action) {
        CoalescedJob job = COALESCED_JOBS.computeIfAbsent(coalesceKey, ignored -> new CoalescedJob());
        synchronized (job) {
            // The newest request supersedes any queued one: both recompute the same dataset.
            job.pending = action;
            if (job.running) {
                // A worker is already active for this key and will pick the request up.
                return;
            }
            job.running = true;
        }
        AFTER_COMMIT_EXECUTOR.execute(() -> drain(coalesceKey, job));
    }

    private static void drain(Object coalesceKey, CoalescedJob job) {
        boolean released = false;
        try {
            while (true) {
                Runnable next;
                synchronized (job) {
                    if (job.pending == null) {
                        job.running = false;
                        COALESCED_JOBS.remove(coalesceKey, job);
                        released = true;
                        return;
                    }
                    next = job.pending;
                    job.pending = null;
                }
                runLogged(next);
            }
        } finally {
            if (!released) {
                // Never leave the key latched as running, or it would stop accepting work.
                synchronized (job) {
                    job.running = false;
                    COALESCED_JOBS.remove(coalesceKey, job);
                }
            }
        }
    }

    private static void runLogged(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOGGER.error("Post-commit async action failed", exception);
        }
    }
}
