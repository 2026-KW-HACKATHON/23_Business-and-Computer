package com.gakkum.backend.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentMappingTest {

    @Test
    @DisplayName("결제 테이블은 필수 값과 승인 후 값을 구분하고 도메인 간 외래 키를 만들지 않는다")
    void mapsPaymentFields() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .build();

        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(Payment.class).buildMetadata();
            Table payments = metadata.getEntityBinding(Payment.class.getName()).getTable();

            assertThat(payments.getName()).isEqualTo("payments");
            for (String name : new String[] { "owner_user_id", "order_id",
                    "amount", "refund_policy_agreed_at", "status" }) {
                assertThat(payments.getColumn(new Column(name)).isNullable()).as(name).isFalse();
            }
            // 제안 결제는 준비 중에 의뢰·지원서가 없다. 일반 결제의 필수 조건은 DB CHECK 제약이 지킨다
            for (String name : new String[] { "job_id", "job_application_id", "proposal_id", "revision_count",
                    "message_to_student" }) {
                assertThat(payments.getColumn(new Column(name)).isNullable()).as(name).isTrue();
            }
            assertThat(payments.getColumn(new Column("message_to_student")).getSqlType(metadata)).isEqualTo("TEXT");
            assertThat(payments.getColumn(new Column("payment_key")).isNullable()).isTrue();
            assertThat(payments.getColumn(new Column("payment_key")).getSqlType(metadata)).isEqualTo("TEXT");
            assertThat(payments.getColumn(new Column("kakao_tid")).isNullable()).isTrue();
            assertThat(payments.getColumn(new Column("kakao_tid")).isUnique()).isTrue();
            assertThat(payments.getColumn(new Column("approved_at")).isNullable()).isTrue();
            for (String name : new String[] { "refund_amount", "student_compensation_amount", "refunded_at" }) {
                assertThat(payments.getColumn(new Column(name)).isNullable()).as(name).isTrue();
            }
            assertThat(payments.getForeignKeyCollection()).isEmpty();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
