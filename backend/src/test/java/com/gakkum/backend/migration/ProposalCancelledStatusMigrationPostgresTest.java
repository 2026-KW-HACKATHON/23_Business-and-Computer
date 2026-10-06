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
class ProposalCancelledStatusMigrationPostgresTest {

    @Test
    @DisplayName("V43은 기존 제안의 상태를 보존하며 CANCELLED 저장을 허용하고 정의되지 않은 상태는 계속 거부한다")
    void allowsCancelledAndPreservesExistingStatuses() throws Exception {
        String url = System.getenv("DATABASE_URL");
        String schema = "v43_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("create schema " + schema);
            try {
                statement.execute("set search_path to " + schema);
                flyway(url, schema, "42").migrate();
                for (String status : List.of("PENDING", "AWAITING_START", "ACCEPTED", "REJECTED")) {
                    insertProposal(statement, status);
                }
                // V43 전에는 CANCELLED를 저장할 수 없다
                assertThatThrownBy(() -> insertProposal(statement, "CANCELLED")).isInstanceOf(SQLException.class);

                flyway(url, schema, "43").migrate();
                insertProposal(statement, "CANCELLED");
                statement.execute("update proposals set status = 'CANCELLED' where title = 'PENDING'");
                statement.execute("update proposals set status = 'PENDING' where title = 'PENDING'");
                assertThatThrownBy(() -> insertProposal(statement, "CANCELED")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> insertProposal(statement, "cancelled")).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.execute(
                        "update proposals set status = null where title = 'PENDING'"))
                        .isInstanceOf(SQLException.class);

                List<String> rows = new ArrayList<>();
                try (ResultSet result = statement.executeQuery("select title, status from proposals order by id")) {
                    while (result.next()) {
                        rows.add(result.getString(1) + "=" + result.getString(2));
                    }
                }
                assertThat(rows).containsExactly("PENDING=PENDING", "AWAITING_START=AWAITING_START",
                        "ACCEPTED=ACCEPTED", "REJECTED=REJECTED", "CANCELLED=CANCELLED");
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
