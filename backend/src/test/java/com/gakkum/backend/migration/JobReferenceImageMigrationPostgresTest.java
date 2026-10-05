package com.gakkum.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
class JobReferenceImageMigrationPostgresTest {

    @Test
    @DisplayName("V42는 기존 의뢰를 보존하며 참고 사진을 빈 배열로 채우고 이전 형태의 INSERT도 허용한다")
    void preservesExistingJobsAndDefaultsNewRows() throws Exception {
        String url = System.getenv("DATABASE_URL");
        String schema = "v42_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            try {
                statement.execute("set search_path to " + schema);
                flyway(url, schema, "41").migrate();
                insertLegacyJob(statement, "기존 의뢰");

                flyway(url, schema, "42").migrate();
                insertLegacyJob(statement, "사진 없이 신규 등록");

                try (ResultSet result = statement.executeQuery(
                        "select title, reference_image_urls::text from jobs order by id")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("기존 의뢰");
                    assertThat(result.getString(2)).isEqualTo("[]");
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("사진 없이 신규 등록");
                    assertThat(result.getString(2)).isEqualTo("[]");
                    assertThat(result.next()).isFalse();
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

    private void insertLegacyJob(Statement statement, String title) throws Exception {
        statement.execute("""
                insert into jobs (owner_profile_id, title, description, budget, draft_deadline, final_deadline,
                    revision_count, status)
                values (5, '%s', '설명', 100000, '2999-01-01', '2999-01-15', 1, 'OPEN')
                """.formatted(title));
    }
}
