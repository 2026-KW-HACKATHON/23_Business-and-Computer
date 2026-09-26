package com.gakkum.backend.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatRoomMappingTest {

    @Test
    @DisplayName("채팅방은 의뢰별로 하나만 생성되도록 매핑된다")
    void mapsOneRoomPerJobWithoutForeignKey() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .build();

        try {
            var metadata = new MetadataSources(registry)
                    .addAnnotatedClass(ChatRoom.class)
                    .buildMetadata();
            Table rooms = metadata.getEntityBinding(ChatRoom.class.getName()).getTable();

            assertThat(rooms.getName()).isEqualTo("chat_rooms");
            assertThat(rooms.getColumn(new Column("id")).getLength()).isEqualTo(26L);
            assertThat(rooms.getColumn(new Column("job_id")).isNullable()).isFalse();
            assertThat(rooms.getColumn(new Column("created_at")).isNullable()).isFalse();
            assertThat(rooms.getUniqueKey("chat_rooms_job_id_key").getColumns())
                    .extracting(Column::getName).containsExactly("job_id");
            assertThat(rooms.getForeignKeyCollection()).isEmpty();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    @DisplayName("채팅방 생성 시 26자리 ULID를 부여한다")
    void createsRoomWithUlid() {
        ChatRoom room = ChatRoom.create(1L);

        assertThat(room.getId()).matches("[0-7][0-9A-HJKMNP-TV-Z]{25}");
        assertThat(room.getJobId()).isEqualTo(1L);
    }
}
