package com.gakkum.backend.domain.student.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.gakkum.backend.domain.student.entity.Student;

import jakarta.persistence.EntityManager;

/**
 * 컬럼 기본값과 NOT NULL 매핑은 Flyway가 적용된 PostgreSQL에서만 확인할 수 있어 실제 DB로 검증한다.
 * 공용 DB에 테스트 행을 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 트랜잭션은 테스트마다 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("학생 패널티 횟수 PostgreSQL 매핑 (컬럼 기본값·저장·재조회)")
class StudentPenaltyCountPostgresTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("패널티 횟수 없이 넣은 행은 컬럼 기본값 0으로 조회된다")
    void readsColumnDefaultAsZero() {
        String unique = unique();
        entityManager.createNativeQuery("""
                insert into student_profiles (user_id, university, student_number, major)
                values (:userId, '광운대학교', :studentNumber, '소프트웨어학부')
                """)
                .setParameter("userId", unique.substring(0, 26))
                .setParameter("studentNumber", unique)
                .executeUpdate();

        Student student = studentRepository.findByUserId(unique.substring(0, 26)).orElseThrow();

        assertThat(student.getPenaltyCount()).isZero();
    }

    @Test
    @DisplayName("새로 만든 학생은 패널티 횟수 0으로 저장되고 재조회된다")
    void savesNewStudentWithZero() {
        String unique = unique();
        Long id = studentRepository.saveAndFlush(Student.create(unique.substring(0, 26), "광운대학교", unique,
                "소프트웨어학부", null, null, null)).getId();
        entityManager.clear();

        assertThat(studentRepository.findById(id).orElseThrow().getPenaltyCount()).isZero();
    }

    @Test
    @DisplayName("저장된 양수 패널티 횟수는 재조회해도 그대로 매핑된다")
    void readsStoredPenaltyCount() {
        String unique = unique();
        Long id = studentRepository.saveAndFlush(Student.builder()
                .userId(unique.substring(0, 26))
                .university("광운대학교")
                .studentNumber(unique)
                .major("소프트웨어학부")
                .penaltyCount(2)
                .build()).getId();
        entityManager.clear();

        assertThat(studentRepository.findById(id).orElseThrow().getPenaltyCount()).isEqualTo(2);
    }

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
