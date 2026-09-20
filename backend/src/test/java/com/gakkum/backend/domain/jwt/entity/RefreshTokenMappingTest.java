package com.gakkum.backend.domain.jwt.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.junit.jupiter.api.Test;

class RefreshTokenMappingTest {

    @Test
    void mapsRefreshTokenColumns() {
        var registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .build();

        try {
            var metadata = new MetadataSources(registry)
                .addAnnotatedClass(RefreshToken.class)
                .buildMetadata();
            var table = metadata.getEntityBinding(RefreshToken.class.getName()).getTable();

            assertThat(table.getName()).isEqualTo("refresh_tokens");
            assertThat(table.getColumns()).extracting(Column::getName)
                .containsExactlyInAnyOrder("id", "username", "refresh", "created_at");
            assertThat(table.getColumn(new Column("username")).isNullable()).isFalse();
            assertThat(table.getColumn(new Column("refresh")).isNullable()).isFalse();
            assertThat(table.getColumn(new Column("refresh")).getLength()).isEqualTo(512L);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
