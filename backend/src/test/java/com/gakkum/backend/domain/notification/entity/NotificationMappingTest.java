package com.gakkum.backend.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationMappingTest {

    @Test
    @DisplayName("알림 테이블은 계획한 컬럼 타입·길이·필수 여부로 매핑되고 도메인 간 외래 키를 만들지 않는다")
    void mapsNotificationColumns() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .build();

        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(Notification.class).buildMetadata();
            Table notifications = metadata.getEntityBinding(Notification.class.getName()).getTable();

            assertThat(notifications.getName()).isEqualTo("notifications");
            assertThat(notifications.getColumns()).extracting(Column::getName).containsExactlyInAnyOrder(
                    "id", "event_id", "recipient_user_id", "type", "title", "body", "target_type", "target_id",
                    "read_at", "created_at");
            assertThat(notifications.getColumn(new Column("event_id")).getSqlType(metadata)).isEqualTo("uuid");
            assertThat(notifications.getColumn(new Column("recipient_user_id")).getLength()).isEqualTo(26L);
            assertThat(notifications.getColumn(new Column("title")).getLength()).isEqualTo(255L);
            assertThat(notifications.getColumn(new Column("body")).getSqlType(metadata)).isEqualTo("TEXT");
            assertThat(notifications.getColumn(new Column("target_id")).getLength()).isEqualTo(26L);
            // enum은 순서 값이 아니라 이름 문자열로 저장한다
            for (String name : new String[] { "type", "target_type" }) {
                Column column = notifications.getColumn(new Column(name));
                assertThat(column.getLength()).as(name).isEqualTo(50L);
                assertThat(column.getSqlTypeCode(metadata)).as(name).isEqualTo(Types.VARCHAR);
            }
            for (String name : new String[] { "event_id", "recipient_user_id", "type", "title", "body",
                    "target_type", "target_id", "created_at" }) {
                assertThat(notifications.getColumn(new Column(name)).isNullable()).as(name).isFalse();
            }
            assertThat(notifications.getColumn(new Column("read_at")).isNullable()).isTrue();
            assertThat(notifications.getUniqueKey("notifications_event_id_recipient_user_id_key").getColumns())
                    .extracting(Column::getName).containsExactly("event_id", "recipient_user_id");
            assertThat(notifications.getForeignKeyCollection()).isEmpty();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    @DisplayName("알림은 읽음 시각만 수정할 수 있고 생성 정보는 수정 대상에서 제외된다")
    void allowsUpdatingOnlyReadAt() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .build();

        try {
            PersistentClass notification = new MetadataSources(registry).addAnnotatedClass(Notification.class)
                    .buildMetadata().getEntityBinding(Notification.class.getName());

            for (String name : new String[] { "eventId", "recipientUserId", "type", "title", "body", "targetType",
                    "targetId", "createdAt" }) {
                assertThat(notification.getProperty(name).isUpdatable()).as(name).isFalse();
            }
            assertThat(notification.getProperty("readAt").isUpdatable()).isTrue();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
