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
class ReviewContentNullableMigrationPostgresTest {

    @Test
    @DisplayName("V48은 기존 리뷰 글을 보존하고 글 없는 리뷰를 NULL로 저장·집계할 수 있게 한다")
    void preservesExistingContentAndAllowsNull() throws Exception {
        String url = System.getenv("DATABASE_URL");
        String schema = "v48_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            try {
                statement.execute("set search_path to " + schema);
                flyway(url, schema, "47").migrate();
                insertReview(statement, 1, "'꼼꼼하게 작업해 주셨어요.'", 5);
                // 칩을 글로 대신 보낸 과거 데이터도 그대로 둔다
                insertReview(statement, 2, "'결과물이 만족스러워요, 친절해요'", 4);
                assertThatThrownBy(() -> insertReview(statement, 3, "null", 3))
                        .isInstanceOf(SQLException.class);

                flyway(url, schema, "48").migrate();
                insertReview(statement, 3, "null", 3);

                List<String> rows = new ArrayList<>();
                try (ResultSet result = statement.executeQuery("""
                        select job_id, coalesce(content, '<NULL>'), rating, positive_points::text
                        from reviews order by job_id
                        """)) {
                    while (result.next()) {
                        rows.add(String.join("|", result.getString(1), result.getString(2), result.getString(3),
                                result.getString(4)));
                    }
                }
                assertThat(rows).containsExactly(
                        "1|꼼꼼하게 작업해 주셨어요.|5|[\"KINDNESS\"]",
                        "2|결과물이 만족스러워요, 친절해요|4|[\"KINDNESS\"]",
                        "3|<NULL>|3|[\"KINDNESS\"]");

                try (ResultSet result = statement.executeQuery("""
                        select count(*), avg(rating), count(content) from reviews where student_profile_id = 7
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).isEqualTo(3L);
                    assertThat(result.getDouble(2)).isEqualTo(4.0);
                    assertThat(result.getLong(3)).isEqualTo(2L);
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

    private void insertReview(Statement statement, int jobId, String content, int rating) throws SQLException {
        statement.execute("""
                insert into reviews (job_id, owner_profile_id, student_profile_id, positive_points, content, rating)
                values (%d, 5, 7, '["KINDNESS"]'::jsonb, %s, %d)
                """.formatted(jobId, content, rating));
    }
}
