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
class ProposalRejectionDetailsMigrationPostgresTest {

    @Test
    @DisplayName("V46은 기존 제안의 상태를 보존하며 거절 주체·시각을 null로 추가하고 OWNER·STUDENT만 저장을 허용한다")
    void addsNullableRejectionDetailsAndPreservesExistingRows() throws Exception {
        String url = System.getenv("DATABASE_URL");
        String schema = "v46_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            try {
                statement.execute("set search_path to " + schema);
                flyway(url, schema, "45").migrate();
                for (String status : List.of("PENDING", "AWAITING_START", "ACCEPTED", "REJECTED", "CANCELLED")) {
                    insertProposal(statement, status);
                }
                // V46 전에는 거절 주체 컬럼이 없다
                assertThatThrownBy(() -> statement.execute("update proposals set rejected_by = 'OWNER'"))
                        .isInstanceOf(SQLException.class);

                flyway(url, schema, "46").migrate();

                // 기존 행은 상태가 그대로이고 기존 거절 제안을 포함해 새 컬럼이 모두 null이다
                List<String> rows = new ArrayList<>();
                try (ResultSet result = statement.executeQuery(
                        "select title, status, rejected_by, rejected_at from proposals order by id")) {
                    while (result.next()) {
                        rows.add(result.getString(1) + "=" + result.getString(2) + "/" + result.getString(3)
                                + "/" + result.getString(4));
                    }
                }
                assertThat(rows).containsExactly("PENDING=PENDING/null/null",
                        "AWAITING_START=AWAITING_START/null/null", "ACCEPTED=ACCEPTED/null/null",
                        "REJECTED=REJECTED/null/null", "CANCELLED=CANCELLED/null/null");

                // 새 제안은 두 컬럼 없이 저장되고, 거절 주체는 OWNER·STUDENT만 받는다
                insertProposal(statement, "PENDING");
                statement.execute("update proposals set status = 'REJECTED', rejected_by = 'OWNER', "
                        + "rejected_at = timestamp '2026-10-07 03:00:00.123456' where title = 'PENDING'");
                statement.execute("update proposals set rejected_by = 'STUDENT', "
                        + "rejected_at = timestamp '2026-10-07 03:00:00' where title = 'AWAITING_START'");
                for (String invalid : List.of("owner", "ADMIN", "")) {
                    assertThatThrownBy(() -> statement.execute(
                            "update proposals set rejected_by = '" + invalid + "' where title = 'ACCEPTED'"))
                            .isInstanceOf(SQLException.class);
                }
                try (ResultSet result = statement.executeQuery("select count(*), min(rejected_at)::text "
                        + "from proposals where rejected_by = 'OWNER'")) {
                    result.next();
                    assertThat(result.getInt(1)).isEqualTo(2);
                    assertThat(result.getString(2)).isEqualTo("2026-10-07 03:00:00.123456");
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

    // 제목에 상태를 그대로 넣어 마이그레이션 뒤에 어느 행인지 알아본다
    private void insertProposal(Statement statement, String status) throws SQLException {
        statement.execute("""
                insert into proposals (student_profile_id, owner_profile_id, title, customer_problem,
                    proposed_solution, work_plan, proposed_fee, draft_days, final_days, reference_image_urls, status)
                values (7, 5, '%s', 'p', 's', 'w', 1000, 0, 1, '[]'::jsonb, '%s')
                """.formatted(status, status));
    }
}
