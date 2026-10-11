package com.balancify.backend.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * What emails grouped as one person did before they were grouped, made into what one person does:
 * one like, one read, one view, one vote and one prediction each, and one point account. Nothing
 * is taken out of the point ledger: the other accounts' rows move to the person's account, and
 * what was earned twice for the same thing is given back by a PERSON_MERGE row of its own.
 */
@Repository
public class AccessPersonMergeRepository {

    public static final String REASON_PERSON_MERGE = "PERSON_MERGE";
    private static final int REFERENCE_MAX_LENGTH = 80;

    public record MergeOutcome(int duplicatesTidied, int predictionsTidied, long pointsGivenBack) {
    }

    private record Transaction(long id, long accountId, String reason, long amount, String referenceKey, LocalDate kstDate) {
    }

    private record Prediction(long id, long matchId, String email) {
    }

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AccessPersonMergeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MergeOutcome merge(Long personId) {
        MapSqlParameterSource person = new MapSqlParameterSource("personId", personId);
        String emails = "(SELECT normalized_email FROM access_person_emails WHERE person_id = :personId)";

        int duplicates = 0;
        duplicates += keepOne("notice_likes", "notice_id", "liker_email", "id ASC", emails, person);
        duplicates += keepOne("notice_comment_likes", "comment_id", "liker_email", "id ASC", emails, person);
        duplicates += keepOne("board_post_likes", "post_id", "liker_email", "id ASC", emails, person);
        duplicates += keepOne("board_post_views", "post_id", "viewer_email", "id ASC", emails, person);
        duplicates += keepOne("notice_reads", "notice_id", "reader_email", "read_at DESC, id DESC", emails, person);
        duplicates += keepOne("notice_votes", "notice_id", "voter_email", "updated_at DESC, id DESC", emails, person);

        // The person's accounts, oldest first: the oldest one becomes theirs.
        Map<String, Long> accountByEmail = new LinkedHashMap<>();
        jdbcTemplate.query(
            "SELECT id, normalized_email FROM point_accounts WHERE normalized_email IN " + emails + " ORDER BY id",
            person,
            row -> {
                accountByEmail.put(row.getString("normalized_email"), row.getLong("id"));
            }
        );
        List<Long> accounts = new ArrayList<>(accountByEmail.values());
        Long primary = accounts.isEmpty() ? null : accounts.getFirst();

        // One prediction a match: the one whose points stay, so the pick and its points agree.
        int predictionsTidied = 0;
        Map<Long, Long> predictionAccountByMatch = new HashMap<>();
        Map<Long, List<Prediction>> predictionsByMatch = new LinkedHashMap<>();
        jdbcTemplate.query(
            "SELECT id, match_id, predictor_email FROM match_predictions WHERE predictor_email IN " + emails
                + " ORDER BY match_id, updated_at DESC, id DESC",
            person,
            row -> {
                Prediction prediction = new Prediction(row.getLong("id"), row.getLong("match_id"), row.getString("predictor_email"));
                predictionsByMatch.computeIfAbsent(prediction.matchId(), ignored -> new ArrayList<>()).add(prediction);
            }
        );
        for (Map.Entry<Long, List<Prediction>> entry : predictionsByMatch.entrySet()) {
            List<Prediction> picks = entry.getValue();
            Prediction kept = picks.stream()
                .filter(pick -> accountByEmail.containsKey(pick.email()))
                .min(Comparator.comparing((Prediction pick) -> accountByEmail.get(pick.email())))
                .orElse(picks.getFirst());
            if (accountByEmail.containsKey(kept.email())) {
                predictionAccountByMatch.put(entry.getKey(), accountByEmail.get(kept.email()));
            }
            for (Prediction pick : picks) {
                if (pick.id() != kept.id()) {
                    predictionsTidied += jdbcTemplate.update(
                        "DELETE FROM match_predictions WHERE id = :id",
                        new MapSqlParameterSource("id", pick.id())
                    );
                }
            }
        }

        long givenBack = 0;
        if (accounts.size() > 1) {
            givenBack = mergeAccounts(primary, accounts, predictionAccountByMatch);
        }
        return new MergeOutcome(duplicates, predictionsTidied, givenBack);
    }

    /** Keeps one row per target among the person's emails, the first by the order given. */
    private int keepOne(
        String table,
        String targetColumn,
        String emailColumn,
        String order,
        String emails,
        MapSqlParameterSource person
    ) {
        return jdbcTemplate.update(
            "DELETE FROM " + table + " t USING ("
                + " SELECT id, row_number() OVER (PARTITION BY " + targetColumn + " ORDER BY " + order + ") AS place"
                + " FROM " + table + " WHERE " + emailColumn + " IN " + emails
                + ") ranked WHERE t.id = ranked.id AND ranked.place > 1",
            person
        );
    }

    /**
     * Moves every row of the other accounts to the primary one. For each thing earned for (a
     * reference key, a prediction's sequence left out) the rows of one account stay as they are:
     * the primary's when it has some, otherwise the oldest account's, and for a prediction the
     * account of the pick that was kept. What the other accounts earned for it is given back.
     */
    private long mergeAccounts(Long primary, List<Long> accounts, Map<Long, Long> predictionAccountByMatch) {
        List<Transaction> rows = jdbcTemplate.query(
            "SELECT id, account_id, reason, amount, reference_key, kst_date FROM point_transactions "
                + "WHERE account_id IN (:accounts) ORDER BY id",
            new MapSqlParameterSource("accounts", accounts),
            (row, rowNumber) -> new Transaction(
                row.getLong("id"),
                row.getLong("account_id"),
                row.getString("reason"),
                row.getLong("amount"),
                row.getString("reference_key"),
                row.getObject("kst_date", LocalDate.class)
            )
        );

        Map<String, Map<Long, List<Transaction>>> byThing = new LinkedHashMap<>();
        for (Transaction row : rows) {
            byThing.computeIfAbsent(thingOf(row.referenceKey()), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(row.accountId(), ignored -> new ArrayList<>())
                .add(row);
        }

        // Rows moving in under a key the primary already holds for that reason get the row id added.
        Map<String, Boolean> primaryKeys = new HashMap<>();
        for (Transaction row : rows) {
            if (row.accountId() == primary) {
                primaryKeys.put(row.reason() + "\u0000" + row.referenceKey(), true);
            }
        }
        for (Transaction row : rows) {
            if (row.accountId() == primary) {
                continue;
            }
            String key = row.referenceKey();
            if (primaryKeys.containsKey(row.reason() + "\u0000" + key)) {
                String suffix = "~" + row.id();
                key = key.substring(0, Math.min(key.length(), REFERENCE_MAX_LENGTH - suffix.length())) + suffix;
            }
            primaryKeys.put(row.reason() + "\u0000" + key, true);
            jdbcTemplate.update(
                "UPDATE point_transactions SET account_id = :primary, reference_key = :key WHERE id = :id",
                new MapSqlParameterSource().addValue("primary", primary).addValue("key", key).addValue("id", row.id())
            );
        }

        long givenBack = 0;
        for (Map.Entry<String, Map<Long, List<Transaction>>> thing : byThing.entrySet()) {
            Map<Long, List<Transaction>> perAccount = thing.getValue();
            if (perAccount.size() < 2) {
                continue;
            }
            Long kept = keptAccount(thing.getKey(), perAccount, primary, accounts, predictionAccountByMatch);
            for (Map.Entry<Long, List<Transaction>> account : perAccount.entrySet()) {
                if (account.getKey().equals(kept)) {
                    continue;
                }
                long net = account.getValue().stream().mapToLong(Transaction::amount).sum();
                if (net == 0) {
                    continue;
                }
                LocalDate day = account.getValue().stream().map(Transaction::kstDate).max(LocalDate::compareTo).orElseThrow();
                String reference = givenBackReference(thing.getKey(), account.getKey(), primary);
                jdbcTemplate.update(
                    "INSERT INTO point_transactions (account_id, reason, amount, reference_key, kst_date, memo) "
                        + "VALUES (:primary, :reason, :amount, :reference, :day, :memo)",
                    new MapSqlParameterSource()
                        .addValue("primary", primary)
                        .addValue("reason", REASON_PERSON_MERGE)
                        .addValue("amount", -net)
                        .addValue("reference", reference)
                        .addValue("day", day)
                        .addValue("memo", "같은 사람으로 묶으며 겹친 적립 정리")
                );
                givenBack += net;
            }
        }

        jdbcTemplate.update(
            "DELETE FROM point_accounts WHERE id IN (:others)",
            new MapSqlParameterSource("others", accounts.subList(1, accounts.size()))
        );
        return givenBack;
    }

    private Long keptAccount(
        String thing,
        Map<Long, List<Transaction>> perAccount,
        Long primary,
        List<Long> accounts,
        Map<Long, Long> predictionAccountByMatch
    ) {
        Long matchId = predictionMatch(thing);
        if (matchId != null && perAccount.containsKey(predictionAccountByMatch.get(matchId))) {
            return predictionAccountByMatch.get(matchId);
        }
        if (perAccount.containsKey(primary)) {
            return primary;
        }
        return accounts.stream().filter(perAccount::containsKey).findFirst().orElse(primary);
    }

    /**
     * A prediction's points are kept as a sequence (prediction:12#1, #2, ...) whose sum is what it
     * earned, so its give-back joins that sequence, where syncPredictionPoint adds it up. Anything
     * else gets a key of its own.
     */
    private String givenBackReference(String thing, Long account, Long primary) {
        if (predictionMatch(thing) != null) {
            Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM point_transactions WHERE account_id = :primary AND reference_key LIKE :prefix",
                new MapSqlParameterSource().addValue("primary", primary).addValue("prefix", thing + "#%"),
                Long.class
            );
            return thing + "#" + ((count == null ? 0 : count) + 1);
        }
        String reference = "merge:" + account + ":" + thing;
        return reference.length() <= REFERENCE_MAX_LENGTH ? reference : reference.substring(0, REFERENCE_MAX_LENGTH);
    }

    static String thingOf(String referenceKey) {
        String key = referenceKey == null ? "" : referenceKey;
        int tilde = key.indexOf('~');
        if (tilde >= 0) {
            key = key.substring(0, tilde);
        }
        int hash = key.indexOf('#');
        return hash >= 0 ? key.substring(0, hash) : key;
    }

    private static Long predictionMatch(String thing) {
        if (!thing.startsWith("prediction:")) {
            return null;
        }
        try {
            return Long.parseLong(thing.substring("prediction:".length()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

}
