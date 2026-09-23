package com.gakkum.backend.domain.job.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

class JobMappingTest {

    @Test
    void mapsJobTablesWithoutForeignKeys() throws NoSuchFieldException {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .build();

        try {
            var metadata = new MetadataSources(registry)
                    .addAnnotatedClass(Job.class)
                    .addAnnotatedClass(JobSpecialty.class)
                    .addAnnotatedClass(JobApplication.class)
                    .buildMetadata();

            Table jobs = metadata.getEntityBinding(Job.class.getName()).getTable();
            assertThat(jobs.getName()).isEqualTo("jobs");
            assertNotNull(jobs, "owner_profile_id", "title", "description", "budget", "draft_deadline",
                    "final_deadline", "revision_count", "status");
            assertThat(jobs.getColumn(new Column("selected_student_profile_id")).isNullable()).isTrue();
            assertThat(jobs.getColumn(new Column("description")).getSqlType(metadata)).isEqualTo("TEXT");
            assertThat(jobs.getColumn(new Column("budget")).getSqlType(metadata)).isEqualTo("bigint");
            assertThat(jobs.getColumn(new Column("draft_deadline")).getSqlType(metadata)).isEqualTo("date");
            assertThat(jobs.getColumn(new Column("final_deadline")).getSqlType(metadata)).isEqualTo("date");
            assertThat(jobs.getColumn(new Column("revision_count")).getSqlType(metadata)).isEqualTo("integer");
            assertThat(jobs.getColumn(new Column("status")).getLength()).isEqualTo(30L);

            Table specialties = metadata.getEntityBinding(JobSpecialty.class.getName()).getTable();
            assertThat(specialties.getName()).isEqualTo("job_specialties");
            assertNotNull(specialties, "job_id", "specialty_id");
            assertThat(specialties.getUniqueKeys()).containsKey("job_specialties_job_id_specialty_id_key");
            assertThat(specialties.getUniqueKey("job_specialties_job_id_specialty_id_key").getColumns())
                    .extracting(Column::getName).containsExactly("job_id", "specialty_id");

            Table applications = metadata.getEntityBinding(JobApplication.class.getName()).getTable();
            assertThat(applications.getName()).isEqualTo("job_applications");
            assertNotNull(applications, "student_profile_id", "job_id", "status");
            assertThat(applications.getColumn(new Column("content")).isNullable()).isTrue();
            assertThat(applications.getColumn(new Column("content")).getSqlType(metadata)).isEqualTo("TEXT");
            assertThat(applications.getColumn(new Column("status")).getLength()).isEqualTo(30L);
            assertThat(applications.getUniqueKeys()).containsKey("job_applications_job_id_student_profile_id_key");
            assertThat(applications.getUniqueKey("job_applications_job_id_student_profile_id_key").getColumns())
                    .extracting(Column::getName).containsExactly("job_id", "student_profile_id");

            assertThat(jobs.getForeignKeyCollection()).isEmpty();
            assertThat(specialties.getForeignKeyCollection()).isEmpty();
            assertThat(applications.getForeignKeyCollection()).isEmpty();
            assertThat(Job.class.getDeclaredField("draftDeadline").getType()).isEqualTo(LocalDate.class);
            assertThat(Job.class.getDeclaredField("finalDeadline").getType()).isEqualTo(LocalDate.class);
            assertThat(Job.class.getDeclaredField("status").getAnnotation(Enumerated.class).value())
                    .isEqualTo(EnumType.STRING);
            assertThat(JobApplication.class.getDeclaredField("status").getAnnotation(Enumerated.class).value())
                    .isEqualTo(EnumType.STRING);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private void assertNotNull(Table table, String... names) {
        for (String name : names) {
            assertThat(table.getColumn(new Column(name))).isNotNull().satisfies(column ->
                    assertThat(column.isNullable()).as(name).isFalse());
        }
    }
}
