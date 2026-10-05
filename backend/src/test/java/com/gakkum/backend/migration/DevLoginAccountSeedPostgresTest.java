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
 * V39가 넣는 테스트 계정은 업종 기준 데이터 유무에 따라 달라져, 테스트마다 임시 스키마에 Flyway를 적용해 검증한다.
 * 공용 DB에 스키마를 만들지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 임시 스키마는 테스트마다 삭제한다.
 */
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("개발용 테스트 로그인 계정 시드 마이그레이션(V39)")
class DevLoginAccountSeedPostgresTest {

    private final String url = System.getenv("DATABASE_URL");
    private final String schema = "v39_test_" + UUID.randomUUID().toString().replace("-", "");
    private Connection connection;

    @BeforeEach
    void createSchema() throws SQLException {
        connection = DriverManager.getConnection(url);
        execute("create schema " + schema);
        execute("set search_path to " + schema);
    }

    @AfterEach
    void dropSchema() throws SQLException {
        execute("drop schema " + schema + " cascade");
        connection.close();
    }

    @Test
    @DisplayName("업종이 없는 DB에서는 대체 업종을 하나 만들고 가입이 끝난 테스트 사장님·학생을 넣는다")
    void seedsAccountsWithFallbackCategoryOnEmptyDatabase() throws SQLException {
        flyway("39").migrate();

        assertThat(queryString("select role || ':' || is_lock || ':' || name from users where username = 'DEV_OWNER'"))
                .isEqualTo("OWNER:false:테스트 사장님");
        assertThat(queryString("select role || ':' || is_lock || ':' || name || ':' || email from users "
                + "where username = 'DEV_STUDENT'")).isEqualTo("STUDENT:false:테스트 학생:dev-student@example.com");
        assertThat(queryString("select count(*) from users")).isEqualTo("2");

        assertThat(queryString("select name from business_categories")).isEqualTo("기타");
        assertThat(queryString("select o.store_name || ':' || c.name from owner_profiles o "
                + "join users u on u.user_id = o.user_id join business_categories c on c.id = o.category_id "
                + "where u.username = 'DEV_OWNER'")).isEqualTo("가꿈 테스트 매장:기타");
        assertThat(queryString("select s.university || ':' || s.student_number || ':' || s.penalty_count "
                + "from student_profiles s join users u on u.user_id = s.user_id where u.username = 'DEV_STUDENT'"))
                .isEqualTo("광운대학교:2099000001:0");
    }

    @Test
    @DisplayName("업종이 이미 있는 DB에서는 업종을 추가하지 않고 가장 작은 ID의 업종을 테스트 매장에 쓴다")
    void usesExistingCategoryWithoutAddingOne() throws SQLException {
        flyway("38").migrate();
        execute("insert into business_categories (id, name) values (7, '카페'), (5, '음식점')");

        flyway("39").migrate();

        assertThat(queryString("select count(*) from business_categories")).isEqualTo("2");
        assertThat(queryString("select category_id from owner_profiles where business_number = 'DEV-LOGIN-OWNER'"))
                .isEqualTo("5");
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
