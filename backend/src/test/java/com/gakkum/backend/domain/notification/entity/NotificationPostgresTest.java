package com.gakkum.backend.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** 로컬 PostgreSQL의 독립 스키마에 Flyway 마이그레이션을 적용해 알림 스키마와 엔티티 저장을 검증한다. */
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
class NotificationPostgresTest {

    private static final String RECIPIENT = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String OTHER_RECIPIENT = "01K58M6PJV8VAJMXHBHJ2PNB5D";

    private static String url;
    private static String schema;
    private static Connection connection;
    private static Statement statement;

    @BeforeAll
    static void migrate() throws Exception {
        url = System.getenv("DATABASE_URL");
        schema = "notifications_test_" + UUID.randomUUID().toString().replace("-", "");
        connection = DriverManager.getConnection(url);
        statement = connection.createStatement();
        statement.execute("create schema " + schema);
        statement.execute("set search_path to " + schema);
        Flyway.configure().dataSource(url, null, null).schemas(schema)
                .locations("classpath:db/migration").target("45").load().migrate();
    }

    @AfterAll
    static void dropSchema() throws Exception {
        try (Connection ignored = connection; Statement drop = statement) {
            drop.execute("drop schema " + schema + " cascade");
        }
    }

    @BeforeEach
    void clear() throws Exception {
        statement.execute("delete from notifications");
    }

    @Test
    @DisplayName("V45는 필수 컬럼이 비어 있는 알림을 거부하고 읽음 시각은 비워 둘 수 있다")
    void rejectsMissingRequiredColumns() throws Exception {
        for (String column : new String[] { "event_id", "recipient_user_id", "type", "title", "body",
                "target_type", "target_id" }) {
            Map<String, String> values = values(UUID.randomUUID(), RECIPIENT, "42");
            values.put(column, "null");

            assertThatThrownBy(() -> statement.execute(insertSql(values)))
                    .as(column).isInstanceOf(SQLException.class).hasMessageContaining("\"" + column + "\"");
        }
        assertThatThrownBy(() -> statement.execute("update notifications set created_at = null"
                + " where id = " + insert(UUID.randomUUID(), RECIPIENT, "42")))
                .isInstanceOf(SQLException.class).hasMessageContaining("created_at");

        try (ResultSet result = statement.executeQuery("select count(*), count(read_at) from notifications")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
            assertThat(result.getInt(2)).isZero();
        }
    }

    @Test
    @DisplayName("생성 시각을 지정하지 않으면 DB가 저장 시점으로 기록하고 숫자 ID와 ULID 대상을 모두 저장한다")
    void defaultsCreatedAtAndStoresNumericAndUlidTargets() throws Exception {
        insert(UUID.randomUUID(), RECIPIENT, "42");
        insert(UUID.randomUUID(), RECIPIENT, "01K58M6PJV8VAJMXHBHJ2PNB5E");

        try (ResultSet result = statement.executeQuery("""
                select target_id, created_at between current_timestamp - interval '1 minute' and current_timestamp
                from notifications order by id
                """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("42");
            assertThat(result.getBoolean(2)).isTrue();
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("01K58M6PJV8VAJMXHBHJ2PNB5E");
            assertThat(result.getBoolean(2)).isTrue();
            assertThat(result.next()).isFalse();
        }
    }

    @Test
    @DisplayName("같은 이벤트·수신자의 알림은 거부하고 다른 수신자 또는 다른 이벤트의 알림은 허용한다")
    void rejectsDuplicateEventForSameRecipient() throws Exception {
        UUID eventId = UUID.randomUUID();
        insert(eventId, RECIPIENT, "42");

        assertThatThrownBy(() -> insert(eventId, RECIPIENT, "42")).isInstanceOf(SQLException.class)
                .hasMessageContaining("notifications_event_id_recipient_user_id_key");
        insert(eventId, OTHER_RECIPIENT, "42");
        // 같은 사용자·유형·대상이어도 이벤트가 다르면 별도 알림으로 누적한다
        insert(UUID.randomUUID(), RECIPIENT, "42");

        try (ResultSet result = statement.executeQuery("select count(*) from notifications")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("V45는 수신자별 최신순 목록 인덱스를 만들고 외래 키는 만들지 않는다")
    void createsListIndexWithoutForeignKeys() throws Exception {
        try (ResultSet result = statement.executeQuery("""
                select indexdef from pg_indexes
                where schemaname = current_schema()
                  and indexname = 'notifications_recipient_user_id_created_at_id_idx'
                """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).endsWith("(recipient_user_id, created_at DESC, id DESC)");
        }
        try (ResultSet result = statement.executeQuery("""
                select count(*) from information_schema.table_constraints
                where table_schema = current_schema() and table_name = 'notifications'
                  and constraint_type = 'FOREIGN KEY'
                """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("엔티티는 마이그레이션 스키마 검증을 통과하고 저장 시 생성 시각을 자동 기록하며 읽음 시각만 갱신한다")
    void persistsEntityAgainstMigratedSchema() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.default_schema", schema)
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .build();
        UUID eventId = UUID.randomUUID();
        LocalDateTime readAt = LocalDateTime.of(2026, 10, 7, 9, 30, 15, 123_456_000);

        try (SessionFactory sessionFactory = new MetadataSources(registry).addAnnotatedClass(Notification.class)
                .buildMetadata().buildSessionFactory()) {
            LocalDateTime before = LocalDateTime.now();
            Long id = sessionFactory.fromTransaction(session -> {
                Notification notification = Notification.create(eventId, RECIPIENT,
                        NotificationType.CHAT_MESSAGE_RECEIVED, "새 메시지가 도착했어요", "안녕하세요, 시안 보내드립니다.",
                        NotificationTargetType.CHAT_ROOM, "01K58M6PJV8VAJMXHBHJ2PNB5E");
                session.persist(notification);
                return notification.getId();
            });

            Notification saved = sessionFactory.fromSession(session -> session.find(Notification.class, id));
            assertThat(saved.getEventId()).isEqualTo(eventId);
            assertThat(saved.getType()).isEqualTo(NotificationType.CHAT_MESSAGE_RECEIVED);
            assertThat(saved.getTargetType()).isEqualTo(NotificationTargetType.CHAT_ROOM);
            assertThat(saved.getReadAt()).isNull();
            assertThat(saved.getCreatedAt()).isCloseTo(before, within(1, ChronoUnit.MINUTES));

            sessionFactory.inTransaction(session -> {
                Notification notification = session.find(Notification.class, id);
                notification.markRead(readAt);
                notification.markRead(readAt.plusDays(1));
            });

            Notification read = sessionFactory.fromSession(session -> session.find(Notification.class, id));
            assertThat(read.getReadAt()).isEqualTo(readAt);
            assertThat(read.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }

        try (ResultSet result = statement.executeQuery(
                "select event_id::text, type, target_type from notifications")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(eventId.toString());
            assertThat(result.getString(2)).isEqualTo("CHAT_MESSAGE_RECEIVED");
            assertThat(result.getString(3)).isEqualTo("CHAT_ROOM");
            assertThat(result.next()).isFalse();
        }
    }

    private long insert(UUID eventId, String recipientUserId, String targetId) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                insertSql(values(eventId, recipientUserId, targetId)) + " returning id")) {
            result.next();
            return result.getLong(1);
        }
    }

    // created_at은 넣지 않아 DB 기본값을 쓴다
    private Map<String, String> values(UUID eventId, String recipientUserId, String targetId) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("event_id", "'" + eventId + "'");
        values.put("recipient_user_id", "'" + recipientUserId + "'");
        values.put("type", "'JOB_APPLICATION_RECEIVED'");
        values.put("title", "'새 지원자가 있어요'");
        values.put("body", "'본문'");
        values.put("target_type", "'JOB'");
        values.put("target_id", "'" + targetId + "'");
        return values;
    }

    private String insertSql(Map<String, String> values) {
        return "insert into notifications (" + String.join(", ", values.keySet()) + ") values ("
                + String.join(", ", values.values()) + ")";
    }
}
