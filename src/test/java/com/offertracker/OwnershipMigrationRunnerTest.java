package com.offertracker;

import com.offertracker.entity.User;
import com.offertracker.mapper.UserMapper;
import com.offertracker.service.OwnershipMigrationRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnershipMigrationRunnerTest {
    @Test
    void dryRunCountsRowsWithoutUpdating() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); UserMapper users = mock(UserMapper.class);
        when(users.selectById(7L)).thenReturn(activeOwner(7L));
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(2);
        new OwnershipMigrationRunner(jdbc, users, mock(TransactionTemplate.class)).run(new DefaultApplicationArguments("--owner-id=7"));
        verify(jdbc, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void applyUpdatesOnlyNullOwnerRowsForAllResourceTables() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); UserMapper users = mock(UserMapper.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        when(users.selectById(7L)).thenReturn(activeOwner(7L));
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        new OwnershipMigrationRunner(jdbc, users, transactions).run(new DefaultApplicationArguments("--owner-id=7", "--apply"));
        verify(jdbc).update("UPDATE companies SET owner_id = ? WHERE owner_id IS NULL", 7L);
        verify(jdbc).update("UPDATE job_applications SET owner_id = ? WHERE owner_id IS NULL", 7L);
        verify(jdbc).update("UPDATE resumes SET owner_id = ? WHERE owner_id IS NULL", 7L);
        verify(jdbc).update("UPDATE ai_interview_sessions SET owner_id = ? WHERE owner_id IS NULL", 7L);
    }

    private User activeOwner(Long id) { User user = new User(); user.setId(id); user.setStatus("ACTIVE"); return user; }
}
