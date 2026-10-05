package com.gakkum.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * V36의 잔여 데이터 차단과 수동 초기화 SQL(db/manual/reset_job_data.sql)은 V35 스키마에 데이터가 있는 상태에서만
 * 확인할 수 있어, 테스트마다 임시 스키마를 만들고 Flyway를 V35까지 적용한 뒤 검증한다.
 * 공용 DB에 스키마를 만들지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 임시 스키마는 테스트마다 삭제한다.
 */
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("지원서 항목 교체 마이그레이션(V36)과 의뢰 데이터 수동 초기화")
class JobApplicationContentMigrationPostgresTest {

    private static final Path RESET_SQL = Path.of("db/manual/reset_job_data.sql");
    private static final List<String> RESET_TABLES = List.of("jobs", "job_specialties", "job_applications",
            "payments", "job_submissions", "reviews", "chat_rooms", "chat_messages", "chat_attachment_uploads");
    private static final List<String> KEPT_TABLES = List.of("users", "owner_profiles", "student_profiles",
            "specialty_categories", "specialties", "student_specialties", "student_certificates", "proposals",
            "proposal_specialties", "proposal_likes");

    private final String url = System.getenv("DATABASE_URL");
    private final String schema = "v36_test_" + UUID.randomUUID().toString().replace("-", "");
    private Connection connection;

    @BeforeEach
    void migrateToV35() throws SQLException {
        connection = DriverManager.getConnection(url);
        execute("create schema " + schema);
        execute("set search_path to " + schema);
        flyway("35").migrate();
        seedKeptData();
    }

    @AfterEach
    void dropSchema() throws SQLException {
        execute("drop schema " + schema + " cascade");
        connection.close();
    }

    @Test
    @DisplayName("지원서가 남아 있으면 V36은 건수를 알리며 중단하고 스키마와 데이터를 그대로 둔다")
    void rejectsRemainingApplications() throws SQLException {
        seedOpenJobWithApplication();

        assertThatThrownBy(() -> flyway("36").migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("job_applications 1건, payments 0건")
                .hasMessageContaining("db/manual/reset_job_data.sql");

        assertThat(appliedVersion()).isEqualTo("35");
        assertThat(columns("job_applications")).contains("content::YES")
                .noneMatch(column -> column.startsWith("summary") || column.startsWith("work_plan")
                        || column.startsWith("delivery_method"));
        assertThat(queryString("select content from job_applications")).isEqualTo("기존 지원 내용");
        assertThat(count("jobs")).isEqualTo(1);
    }

    @Test
    @DisplayName("지원서 없이 결제만 남아 있어도 V36은 중단하고 결제 행을 그대로 둔다")
    void rejectsRemainingPayments() throws SQLException {
        execute("""
                insert into payments (job_id, job_application_id, owner_user_id, order_id, amount,
                        refund_policy_agreed_at, status)
                values (1, 1, 'owner-user-000000000000001', 'order-1', 100000, now(), 'PAID')
                """);

        assertThatThrownBy(() -> flyway("36").migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("job_applications 0건, payments 1건");

        assertThat(appliedVersion()).isEqualTo("35");
        assertThat(columns("job_applications")).contains("content::YES");
        assertThat(count("payments")).isEqualTo(1);
    }

    @Test
    @DisplayName("초기화 SQL은 의뢰 관련 테이블만 비우고 회원·프로필·특기·제안과 ID 시퀀스는 유지한다")
    void resetDeletesOnlyJobData() throws Exception {
        seedOpenJobWithApplication();
        seedClosedJobWithHistory();
        for (String table : RESET_TABLES) {
            assertThat(count(table)).as(table).isPositive();
        }
        List<Long> keptCounts = counts(KEPT_TABLES);
        long jobSequence = queryLong("select last_value from jobs_id_seq");

        execute(Files.readString(RESET_SQL));

        for (String table : RESET_TABLES) {
            assertThat(count(table)).as(table).isZero();
        }
        assertThat(counts(KEPT_TABLES)).isEqualTo(keptCounts).allMatch(count -> count > 0);
        assertThat(queryLong("select last_value from jobs_id_seq")).isEqualTo(jobSequence);
    }

    @Test
    @DisplayName("초기화 후에는 V36이 적용되어 content가 사라지고 세 필수 컬럼과 중복 방지 제약이 남는다")
    void migratesAfterReset() throws Exception {
        seedOpenJobWithApplication();
        seedClosedJobWithHistory();
        execute(Files.readString(RESET_SQL));

        flyway("36").migrate();

        assertThat(appliedVersion()).isEqualTo("36");
        assertThat(columns("job_applications"))
                .contains("summary:255:NO", "work_plan:500:NO", "delivery_method:500:NO")
                .noneMatch(column -> column.startsWith("content"));
        assertThat(queryLong("select count(*) from pg_constraint where conname = "
                + "'job_applications_job_id_student_profile_id_key' and connamespace = '" + schema
                + "'::regnamespace")).isEqualTo(1);
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(url, null, null)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private void seedKeptData() throws SQLException {
        execute("""
                insert into users (user_id, username, name, role, is_lock) values
                    ('owner-user-000000000000001', 'owner1', '사장님', 'OWNER', false),
                    ('student-user-0000000000001', 'student1', '학생', 'STUDENT', false);
                insert into business_categories (id, name) values (1, '음식점');
                insert into specialty_categories (id, name) values (1, '디자인');
                insert into specialties (id, name, specialty_category_id) values (11, '포스터 디자인', 1);
                insert into owner_profiles (id, user_id, business_number, store_name, category_id)
                    values (1, 'owner-user-000000000000001', '1234567890', '가게', 1);
                insert into student_profiles (id, user_id, university, student_number, major)
                    values (1, 'student-user-0000000000001', '광운대학교', '2023000001', '소프트웨어학부');
                insert into student_specialties (student_profile_id, specialty_id) values (1, 11);
                insert into student_certificates (student_profile_id, certificate_name, acquired_year,
                        issuing_organization)
                    values (1, 'GTQ', 2024, 'KPC');
                insert into proposals (id, student_profile_id, owner_profile_id, title, customer_problem,
                        proposed_solution, work_plan, proposed_fee, draft_days, final_days, reference_image_urls)
                    values (1, 1, 1, '제안', '문제', '해결', '계획', 50000, 3, 7, '[]');
                insert into proposal_specialties (proposal_id, specialty_id) values (1, 11);
                insert into proposal_likes (proposal_id, student_profile_id) values (1, 1);
                """);
    }

    private void seedOpenJobWithApplication() throws SQLException {
        execute("""
                insert into jobs (id, owner_profile_id, title, description, budget, draft_deadline, final_deadline,
                        revision_count, status)
                    values (9001, 1, '모집 중 의뢰', '설명', 50000, '2026-11-10', '2026-11-20', 1, 'OPEN');
                insert into job_specialties (job_id, specialty_id) values (9001, 11);
                insert into job_applications (student_profile_id, job_id, content, status)
                    values (1, 9001, '기존 지원 내용', 'PENDING');
                """);
    }

    /** 완료된 의뢰 한 건에 결제·제출물·리뷰·채팅 이력을 모두 연결한다. */
    private void seedClosedJobWithHistory() throws SQLException {
        execute("""
                insert into jobs (owner_profile_id, title, description, budget, draft_deadline, final_deadline,
                        revision_count, status, selected_student_profile_id, completed_at)
                    values (1, '완료 의뢰', '설명', 100000, '2026-10-10', '2026-10-20', 2, 'CLOSED', 1, now());
                insert into job_specialties (job_id, specialty_id) select id, 11 from jobs where status = 'CLOSED';
                insert into job_applications (id, student_profile_id, job_id, content, status)
                    select 9101, 1, id, '선택된 지원 내용', 'ACCEPTED' from jobs where status = 'CLOSED';
                insert into payments (job_id, job_application_id, owner_user_id, order_id, amount,
                        refund_policy_agreed_at, status)
                    select id, 9101, 'owner-user-000000000000001', 'order-1', 100000, now(), 'PAID'
                    from jobs where status = 'CLOSED';
                insert into job_submissions (job_id, submission_type, revision_number, file_urls, message,
                        review_status)
                    select id, 'DRAFT', 0, '["https://example.com/draft.pdf"]', '초안', 'APPROVED'
                    from jobs where status = 'CLOSED';
                insert into reviews (job_id, owner_profile_id, student_profile_id, positive_points, content, rating)
                    select id, 1, 1, '[]', '좋았어요', 5 from jobs where status = 'CLOSED';
                insert into chat_rooms (id, job_id)
                    select 'room-000000000000000000001', id from jobs where status = 'CLOSED';
                insert into chat_attachment_uploads (id, room_id, uploader_user_id, type, storage_key, file_name,
                        content_type, file_size, status, expires_at)
                    values ('11111111-1111-1111-1111-111111111111', 'room-000000000000000000001',
                        'student-user-0000000000001', 'FILE', 'chat/key', '시안.pdf', 'application/pdf', 10,
                        'ATTACHED', now());
                insert into chat_messages (room_id, sender_user_id, type, content)
                    values ('room-000000000000000000001', 'owner-user-000000000000001', 'TEXT', '안녕하세요');
                insert into chat_messages (room_id, sender_user_id, type, attachment_key, attachment_name,
                        attachment_upload_id)
                    values ('room-000000000000000000001', 'student-user-0000000000001', 'FILE', 'chat/key',
                        '시안.pdf', '11111111-1111-1111-1111-111111111111');
                """);
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long count(String table) throws SQLException {
        return queryLong("select count(*) from " + table);
    }

    private List<Long> counts(List<String> tables) throws SQLException {
        List<Long> counts = new ArrayList<>();
        for (String table : tables) {
            counts.add(count(table));
        }
        return counts;
    }

    private long queryLong(String sql) throws SQLException {
        return Long.parseLong(queryString(sql));
    }

    private String queryString(String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private String appliedVersion() throws SQLException {
        return queryString("select max(version::int) from flyway_schema_history where success");
    }

    /** "이름:최대길이:NULL허용" 형태. 길이가 없는 타입은 "이름::YES"처럼 길이가 빈다. */
    private List<String> columns(String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "select column_name || ':' || coalesce(character_maximum_length::text, '') || ':' || is_nullable "
                        + "from information_schema.columns where table_schema = '" + schema
                        + "' and table_name = '" + table + "'")) {
            while (result.next()) {
                columns.add(result.getString(1));
            }
        }
        return columns;
    }
}
