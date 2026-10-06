package com.gakkum.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
class JobSubmissionRevisionReferenceImageMigrationPostgresTest {

    @Test
    @DisplayName("V44는 기존 제출물을 보존하며 수정 요청 참고 사진을 빈 배열로 채우고 이전 형태의 INSERT도 허용한다")
    void preservesExistingSubmissionsAndDefaultsNewRows() throws Exception {
        String url = System.getenv("DATABASE_URL");
        String schema = "v44_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            try {
                statement.execute("set search_path to " + schema);
                flyway(url, schema, "43").migrate();
                insertLegacySubmission(statement, 1, "PENDING", "null", "null");
                insertLegacySubmission(statement, 2, "REVISION_REQUESTED", "null", "null");
                insertLegacySubmission(statement, 3, "REVISION_REQUESTED", "'기존 코멘트'",
                        "timestamp '2026-09-28 12:15:30'");

                flyway(url, schema, "44").migrate();
                insertLegacySubmission(statement, 4, "PENDING", "null", "null");

                List<String> rows = new ArrayList<>();
                try (ResultSet result = statement.executeQuery("""
                        select job_id, review_status, file_urls::text, message, review_comment, reviewed_at,
                            revision_reference_image_urls::text
                        from job_submissions order by job_id
                        """)) {
                    while (result.next()) {
                        rows.add(String.join("|", result.getString(1), result.getString(2), result.getString(3),
                                result.getString(4), String.valueOf(result.getString(5)),
                                String.valueOf(result.getString(6)), result.getString(7)));
                    }
                }
                assertThat(rows).containsExactly(
                        "1|PENDING|[\"https://example.com/a.pdf\"]|제출 메시지|null|null|[]",
                        "2|REVISION_REQUESTED|[\"https://example.com/a.pdf\"]|제출 메시지|null|null|[]",
                        "3|REVISION_REQUESTED|[\"https://example.com/a.pdf\"]|제출 메시지|기존 코멘트|2026-09-28 12:15:30|[]",
                        "4|PENDING|[\"https://example.com/a.pdf\"]|제출 메시지|null|null|[]");

                statement.execute("""
                        update job_submissions
                        set revision_reference_image_urls = '["https://images.example.com/a.png"]'::jsonb
                        where job_id = 1
                        """);
                for (String invalid : List.of("null", "'{}'::jsonb", "'\"url\"'::jsonb")) {
                    assertThatThrownBy(() -> statement.execute(
                            "update job_submissions set revision_reference_image_urls = " + invalid
                                    + " where job_id = 1"))
                            .isInstanceOf(SQLException.class);
                }
            } finally {
                statement.execute("drop schema " + schema + " cascade");
            }
        }
    }

    private Flyway flyway(String url, String schema, String target) {
        return Flyway.configure().dataSource(url, null, null).schemas(schema)
                .locations("classpath:db/migration").target(target).load();
    }

    // 의뢰마다 초안 하나만 넣어 의뢰별 수정 번호·검토 대기 유니크 제약과 겹치지 않게 한다
    private void insertLegacySubmission(Statement statement, int jobId, String reviewStatus, String reviewComment,
            String reviewedAt) throws SQLException {
        statement.execute("""
                insert into job_submissions (job_id, submission_type, revision_number, file_urls, message,
                    review_status, review_comment, reviewed_at)
                values (%d, 'DRAFT', 0, '["https://example.com/a.pdf"]'::jsonb, '제출 메시지', '%s', %s, %s)
                """.formatted(jobId, reviewStatus, reviewComment, reviewedAt));
    }
}
