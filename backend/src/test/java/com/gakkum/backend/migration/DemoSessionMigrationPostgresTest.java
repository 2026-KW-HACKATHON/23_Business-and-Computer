package com.gakkum.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * V40(공유 테스트 계정 삭제)과 V41(격리 범위 컬럼 추가)은 V39가 넣은 계정과 기존 행이 있는 상태에서만 확인할 수 있어,
 * 테스트마다 임시 스키마에 Flyway를 V39까지 적용한 뒤 검증한다.
 * 공용 DB에 스키마를 만들지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 임시 스키마는 테스트마다 삭제한다.
 */
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("테스트 계정 삭제(V40)와 데모 격리 범위 컬럼 추가(V41) 마이그레이션")
class DemoSessionMigrationPostgresTest {

    private final String url = System.getenv("DATABASE_URL");
    private final String schema = "v41_test_" + UUID.randomUUID().toString().replace("-", "");
    private Connection connection;

    @BeforeEach
    void migrateToV39() throws SQLException {
        connection = DriverManager.getConnection(url);
        execute("create schema " + schema);
        execute("set search_path to " + schema);
        flyway("39").migrate();
    }

    @AfterEach
    void dropSchema() throws SQLException {
        execute("drop schema " + schema + " cascade");
        connection.close();
    }

    @Test
    @DisplayName("V40은 V39가 넣은 테스트 사장님·학생과 프로필·refresh 토큰만 지우고 다른 계정과 업종은 남긴다")
    void removesOnlyDevLoginAccounts() throws SQLException {
        execute("""
                insert into users (user_id, username, name, role, is_lock) values
                    ('real-owner-user-0000000001', 'KAKAO_1', '사장님', 'OWNER', false);
                insert into owner_profiles (user_id, business_number, store_name, category_id)
                    select 'real-owner-user-0000000001', '1234567890', '실제 가게', min(id) from business_categories;
                insert into refresh_tokens (username, refresh) values
                    ('DEV_OWNER', 'dev-refresh'), ('KAKAO_1', 'real-refresh');
                """);
        assertThat(queryString("select count(*) from users where username like 'DEV\\_%'")).isEqualTo("2");

        flyway("40").migrate();

        assertThat(queryString("select string_agg(username, ',') from users")).isEqualTo("KAKAO_1");
        assertThat(queryString("select string_agg(store_name, ',') from owner_profiles")).isEqualTo("실제 가게");
        assertThat(queryString("select count(*) from student_profiles")).isEqualTo("0");
        assertThat(queryString("select string_agg(username, ',') from refresh_tokens")).isEqualTo("KAKAO_1");
        assertThat(queryString("select count(*) from business_categories")).isEqualTo("1");
    }

    @Test
    @DisplayName("V41은 사용자·매장·의뢰·제안에 NULL 허용 격리 범위 컬럼을 더하고 기존 행은 실제 데이터(NULL)로 둔다")
    void addsNullableDemoSessionColumns() throws SQLException {
        flyway("40").migrate();
        execute("""
                insert into users (user_id, username, name, role, is_lock) values
                    ('real-owner-user-0000000001', 'KAKAO_1', '사장님', 'OWNER', false);
                insert into owner_profiles (id, user_id, business_number, store_name, category_id)
                    select 1, 'real-owner-user-0000000001', '1234567890', '실제 가게', min(id) from business_categories;
                insert into jobs (owner_profile_id, title, description, budget, draft_deadline, final_deadline,
                        revision_count, status)
                    values (1, '의뢰', '설명', 50000, '2026-11-10', '2026-11-20', 1, 'OPEN');
                insert into proposals (student_profile_id, owner_profile_id, title, customer_problem,
                        proposed_solution, work_plan, proposed_fee, draft_days, final_days, reference_image_urls)
                    values (1, 1, '제안', '문제', '해결', '계획', 50000, 3, 7, '[]');
                """);

        flyway("41").migrate();

        for (String table : new String[] {"users", "owner_profiles", "jobs", "proposals"}) {
            assertThat(queryString("select character_maximum_length || ':' || is_nullable "
                    + "from information_schema.columns where table_schema = '" + schema + "' and table_name = '"
                    + table + "' and column_name = 'demo_session_id'")).as(table).isEqualTo("26:YES");
            assertThat(queryString("select count(*) || ':' || count(demo_session_id) from " + table))
                    .as(table).isEqualTo("1:0");
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(url, null, null)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String queryString(String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
