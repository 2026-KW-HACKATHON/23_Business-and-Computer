package com.gakkum.backend.domain.owner.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.junit.jupiter.api.Test;

class OwnerMappingTest {

    @Test
    void mapsStoreAddressColumn() {
        var registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .build();

        try {
            var metadata = new MetadataSources(registry)
                .addAnnotatedClass(Owner.class)
                .buildMetadata();
            var table = metadata.getEntityBinding(Owner.class.getName()).getTable();

            assertThat(table.getName()).isEqualTo("owner_profiles");
            assertThat(table.getColumn(new Column("store_address"))).isNotNull();
            assertThat(table.getColumn(new Column("address"))).isNull();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
