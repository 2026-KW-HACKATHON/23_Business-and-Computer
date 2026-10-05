package com.gakkum.backend.domain.job.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.gakkum.backend.domain.job.entity.Job;

import jakarta.persistence.EntityManager;

/** 로컬 PostgreSQL에서 Flyway 스키마와 JSONB 매핑을 검증하고 테스트마다 롤백한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
class JobReferenceImagePostgresTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("참고 사진 여러 장은 PostgreSQL JSONB에 저장 후 영속성 컨텍스트를 비워도 순서를 유지한다")
    void savesAndReloadsImagesInOrder() {
        List<String> images = List.of("https://images.example.com/b.png", "https://images.example.com/a.png");
        Long id = jobRepository.saveAndFlush(job(images)).getId();
        entityManager.clear();

        assertThat(jobRepository.findById(id).orElseThrow().getReferenceImageUrls()).containsExactlyElementsOf(images);
    }

    @Test
    @DisplayName("사진 없는 기존 생성 경로는 빈 JSONB 배열로 저장되고 재조회된다")
    void savesWithoutImages() {
        Long id = jobRepository.saveAndFlush(Job.create(5L, "의뢰", "설명", 100000L,
                LocalDate.of(2999, 1, 1), LocalDate.of(2999, 1, 15), 1, null)).getId();
        entityManager.clear();

        assertThat(jobRepository.findById(id).orElseThrow().getReferenceImageUrls()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "'{}'::jsonb", "'\"url\"'::jsonb"})
    @DisplayName("DB는 NULL과 배열이 아닌 참고 사진 JSON을 거부한다")
    void rejectsNonArrayImages(String sqlValue) {
        Long id = jobRepository.saveAndFlush(job(List.of())).getId();

        assertThatThrownBy(() -> entityManager.createNativeQuery(
                        "update jobs set reference_image_urls = " + sqlValue + " where id = :id")
                .setParameter("id", id).executeUpdate()).isInstanceOf(Exception.class);
    }

    private Job job(List<String> images) {
        return Job.create(5L, "의뢰", "설명", 100000L,
                LocalDate.of(2999, 1, 1), LocalDate.of(2999, 1, 15), 1, images, null);
    }
}
