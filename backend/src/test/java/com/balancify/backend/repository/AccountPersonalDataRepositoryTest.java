package com.balancify.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class AccountPersonalDataRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void removesResultEditingWithTheRestOfTheAccountIdentity() {
        AccountPersonalDataRepository repository = new AccountPersonalDataRepository(jdbcTemplate);

        repository.deleteAccountIdentity(UUID.randomUUID(), "your_username@example.com");

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(statements.capture(), any(SqlParameterSource.class));
        List<String> sql = statements.getAllValues();
        assertThat(sql).contains("DELETE FROM match_result_editor_emails WHERE normalized_email = :email");
        assertThat(sql).anySatisfy(statement -> assertThat(statement)
            .startsWith("UPDATE match_result_editor_emails SET created_by_email = NULL"));
    }

    @Test
    void removesPointsWithTheAccount() {
        AccountPersonalDataRepository repository = new AccountPersonalDataRepository(jdbcTemplate);

        repository.deleteAccountIdentity(UUID.randomUUID(), "your_username@example.com");

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(statements.capture(), any(SqlParameterSource.class));
        List<String> sql = statements.getAllValues();
        assertThat(sql).contains("DELETE FROM point_accounts WHERE normalized_email = :email");
        assertThat(sql).contains("DELETE FROM match_predictions WHERE predictor_email = :email");
        assertThat(sql).contains(
            "UPDATE prize_event_winners SET normalized_email = NULL, nickname = NULL WHERE normalized_email = :email"
        );
        assertThat(sql).anySatisfy(statement -> assertThat(statement)
            .startsWith("UPDATE point_transactions SET created_by_email = NULL"));
    }

    @Test
    void removesWhatTheAccountLeftOnNotices() {
        AccountPersonalDataRepository repository = new AccountPersonalDataRepository(jdbcTemplate);

        repository.deleteAccountIdentity(UUID.randomUUID(), "your_username@example.com");

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(statements.capture(), any(SqlParameterSource.class));
        assertThat(statements.getAllValues()).contains(
            "DELETE FROM notice_reads WHERE reader_email = :email",
            "DELETE FROM notice_likes WHERE liker_email = :email",
            "DELETE FROM notice_comment_likes WHERE liker_email = :email",
            "DELETE FROM notice_votes WHERE voter_email = :email",
            "DELETE FROM notice_comments WHERE author_email = :email"
        );
    }

    @Test
    void removesWhatTheAccountLeftOnTheBoards() {
        AccountPersonalDataRepository repository = new AccountPersonalDataRepository(jdbcTemplate);

        repository.deleteAccountIdentity(UUID.randomUUID(), "your_username@example.com");

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(statements.capture(), any(SqlParameterSource.class));
        assertThat(statements.getAllValues()).contains(
            "DELETE FROM board_post_likes WHERE liker_email = :email",
            "DELETE FROM board_post_views WHERE viewer_email = :email",
            "DELETE FROM board_comments WHERE author_email = :email",
            "DELETE FROM board_posts WHERE author_email = :email"
        );
        // Comments first: removing a post takes the comments on it along, their own included.
        assertThat(statements.getAllValues().indexOf("DELETE FROM board_comments WHERE author_email = :email"))
            .isLessThan(statements.getAllValues().indexOf("DELETE FROM board_posts WHERE author_email = :email"));
    }

    @Test
    void removesTheAccountsNicknameRequestsAndItsNameFromThoseItDecided() {
        AccountPersonalDataRepository repository = new AccountPersonalDataRepository(jdbcTemplate);

        repository.deleteAccountIdentity(UUID.randomUUID(), "your_username@example.com");

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(statements.capture(), any(SqlParameterSource.class));
        assertThat(statements.getAllValues()).contains(
            "DELETE FROM nickname_change_requests WHERE requester_email = :email",
            "UPDATE nickname_change_requests SET processed_by_email = NULL WHERE processed_by_email = :email"
        );
    }

    @Test
    void removesNotificationStateWithTheAccount() {
        AccountPersonalDataRepository repository = new AccountPersonalDataRepository(jdbcTemplate);

        repository.deleteAccountIdentity(UUID.randomUUID(), "your_username@example.com");

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(statements.capture(), any(SqlParameterSource.class));
        assertThat(statements.getAllValues()).contains(
            "DELETE FROM notification_cursors WHERE email = :email",
            "DELETE FROM push_subscriptions WHERE email = :email"
        );
    }

    @Test
    void leavesEmailTablesAloneWithoutAnEmail() {
        AccountPersonalDataRepository repository = new AccountPersonalDataRepository(jdbcTemplate);

        repository.deleteAccountIdentity(UUID.randomUUID(), " ");

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(statements.capture(), any(SqlParameterSource.class));
        assertThat(statements.getValue()).doesNotContain("match_result_editor_emails");
    }
}
