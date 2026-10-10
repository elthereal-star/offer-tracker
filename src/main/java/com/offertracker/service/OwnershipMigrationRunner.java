package com.offertracker.service;

import com.offertracker.entity.User;
import com.offertracker.mapper.UserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** Assigns legacy owner_id NULL rows to an explicitly verified account during cutover. */
@Component
@ConditionalOnProperty(name = "offer-tracker.ownership-migration.enabled", havingValue = "true")
public class OwnershipMigrationRunner implements ApplicationRunner {
    private static final List<String> TABLES = List.of(
            "companies", "job_applications", "resumes", "ai_interview_sessions");
    private final JdbcTemplate jdbc;
    private final UserMapper users;
    private final TransactionTemplate transactions;

    public OwnershipMigrationRunner(JdbcTemplate jdbc, UserMapper users, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.users = users;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long ownerId = ownerId(args);
        User owner = users.selectById(ownerId);
        if (owner == null || !"ACTIVE".equals(owner.getStatus())) {
            throw new IllegalArgumentException("owner-id must identify an active migration account");
        }
        boolean apply = args.containsOption("apply");
        List<RowCount> counts = TABLES.stream().map(table -> new RowCount(table, countUnowned(table))).toList();
        long total = counts.stream().mapToLong(RowCount::count).sum();
        if (apply && total > 0) apply(ownerId);
        System.out.printf("Ownership migration %s for owner %d: %s total=%d%n", apply ? "applied" : "dry-run", ownerId,
                counts.stream().map(RowCount::toString).reduce((a, b) -> a + "," + b).orElse("none"), total);
    }

    private void apply(Long ownerId) {
        transactions.executeWithoutResult(status -> {
            for (String table : TABLES) {
                jdbc.update("UPDATE " + table + " SET owner_id = ? WHERE owner_id IS NULL", ownerId);
            }
        });
    }

    private int countUnowned(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE owner_id IS NULL", Integer.class);
        return count == null ? 0 : count;
    }

    private Long ownerId(ApplicationArguments args) {
        List<String> values = args.getOptionValues("owner-id");
        if (values == null || values.size() != 1) throw new IllegalArgumentException("ownership migration requires exactly one --owner-id");
        try {
            long id = Long.parseLong(values.getFirst());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("owner-id must be a positive integer", ex);
        }
    }

    private record RowCount(String table, int count) { @Override public String toString() { return table + "=" + count; } }
}
