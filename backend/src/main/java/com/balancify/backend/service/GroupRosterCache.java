package com.balancify.backend.service;

import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.PlayerRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A group's roster (its players, highest MMR first) kept in the process between changes. The
 * players list, the tier board and the ranking each read the whole roster, some 130 rows of 33
 * columns, and it is read far more often than it changes. What the database sends out is what
 * Supabase meters (egress).
 *
 * Every change to a player drops every copy: a change saved through the entity once it commits
 * (Hibernate tells this class), and the bulk updates in PlayerRepository through their callers
 * (changed()). A copy read while a change was landing is handed out but not kept. A copy is trusted
 * for MAX_AGE at most, so a change made outside the backend (by hand in the database) shows within
 * it. Copies live in this process only.
 *
 * The players handed out are detached and shared between requests: read them, never change or save
 * them.
 */
@Component
public class GroupRosterCache {
    static final Duration MAX_AGE = Duration.ofMinutes(5);

    private final PlayerRepository playerRepository;
    private final Clock clock;
    private final AtomicLong generation = new AtomicLong();
    private final ConcurrentMap<Long, Copy> copies = new ConcurrentHashMap<>();

    @Autowired
    public GroupRosterCache(PlayerRepository playerRepository, EntityManagerFactory entityManagerFactory) {
        this(playerRepository, Clock.systemUTC());
        EventListenerRegistry listeners = entityManagerFactory
            .unwrap(SessionFactoryImplementor.class)
            .getServiceRegistry()
            .getService(EventListenerRegistry.class);
        PlayerCommits playerCommits = new PlayerCommits(this);
        listeners.appendListeners(EventType.POST_COMMIT_INSERT, playerCommits);
        listeners.appendListeners(EventType.POST_COMMIT_UPDATE, playerCommits);
        listeners.appendListeners(EventType.POST_COMMIT_DELETE, playerCommits);
    }

    GroupRosterCache(PlayerRepository playerRepository, Clock clock) {
        this.playerRepository = playerRepository;
        this.clock = clock;
    }

    /** The group's players, highest MMR first, as PlayerRepository.findByGroup_IdOrderByMmrDescIdAsc reads them. */
    public List<Player> roster(Long groupId) {
        long now = clock.millis();
        long current = generation.get();
        Copy copy = copies.get(groupId);
        if (copy != null && copy.generation() == current && now - copy.readAtMillis() < MAX_AGE.toMillis()) {
            return copy.players();
        }
        List<Player> players = List.copyOf(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId));
        // A change that landed while these were read may be missing from them.
        if (generation.get() == current) {
            copies.put(groupId, new Copy(players, current, now));
        }
        return players;
    }

    /** A player changed: every copy goes, now and again once the surrounding transaction commits. */
    public void changed() {
        TransactionAfterCommit.runNowAndAfterCommit(this::dropAll);
    }

    /** Moves on with every change(): what shows players elsewhere can tell it may be out of date. */
    public long generation() {
        return generation.get();
    }

    private void dropAll() {
        generation.incrementAndGet();
        copies.clear();
    }

    private record Copy(List<Player> players, long generation, long readAtMillis) {
    }

    /** Hibernate's word that a player row was inserted, updated or deleted, given after the commit. */
    private record PlayerCommits(GroupRosterCache cache)
        implements PostCommitInsertEventListener, PostCommitUpdateEventListener, PostCommitDeleteEventListener {

        @Override
        public boolean requiresPostCommitHandling(EntityPersister persister) {
            return Player.class.equals(persister.getMappedClass());
        }

        @Override
        public void onPostInsert(PostInsertEvent event) {
            changedIfPlayer(event.getEntity());
        }

        @Override
        public void onPostUpdate(PostUpdateEvent event) {
            changedIfPlayer(event.getEntity());
        }

        @Override
        public void onPostDelete(PostDeleteEvent event) {
            changedIfPlayer(event.getEntity());
        }

        // A change that did not commit changed nothing.
        @Override
        public void onPostInsertCommitFailed(PostInsertEvent event) {
        }

        @Override
        public void onPostUpdateCommitFailed(PostUpdateEvent event) {
        }

        @Override
        public void onPostDeleteCommitFailed(PostDeleteEvent event) {
        }

        private void changedIfPlayer(Object entity) {
            if (entity instanceof Player) {
                cache.changed();
            }
        }
    }
}
