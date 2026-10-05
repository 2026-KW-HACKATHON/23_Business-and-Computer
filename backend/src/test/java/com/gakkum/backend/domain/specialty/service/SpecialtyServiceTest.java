package com.gakkum.backend.domain.specialty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.domain.specialty.dto.SpecialtyCommandDto.AddStudentSpecialtyCommand;
import com.gakkum.backend.domain.specialty.entity.StudentSpecialty;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.repository.StudentSpecialtyRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class SpecialtyServiceTest {

    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final StudentSpecialtyRepository studentSpecialtyRepository = mock(StudentSpecialtyRepository.class);
    private final SpecialtyService specialtyService = new SpecialtyService(
            specialtyRepository,
            studentSpecialtyRepository);

    @Test
    void addsExistingSpecialtyToStudent() {
        when(specialtyRepository.existsById(1L)).thenReturn(true);
        when(studentSpecialtyRepository.save(any(StudentSpecialty.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddStudentSpecialtyCommand command = AddStudentSpecialtyCommand.of(
                10L,
                1L);

        StudentSpecialty savedSpecialty = specialtyService.addStudentSpecialty(command);

        ArgumentCaptor<StudentSpecialty> specialtyCaptor = ArgumentCaptor.forClass(StudentSpecialty.class);
        verify(studentSpecialtyRepository).save(specialtyCaptor.capture());
        assertThat(savedSpecialty).isSameAs(specialtyCaptor.getValue());
        assertThat(savedSpecialty.getStudentProfileId()).isEqualTo(10L);
        assertThat(savedSpecialty.getSpecialtyId()).isEqualTo(1L);
    }

    @Test
    void rejectsMissingSpecialty() {
        when(specialtyRepository.existsById(99L)).thenReturn(false);

        AddStudentSpecialtyCommand command = AddStudentSpecialtyCommand.of(
                10L,
                99L);

        assertThatThrownBy(() -> specialtyService.addStudentSpecialty(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SPECIALTY_NOT_FOUND));

        verify(studentSpecialtyRepository, never()).save(any(StudentSpecialty.class));
    }

    @Test
    void rejectsDuplicateSpecialtyIds() {
        assertThatThrownBy(() -> specialtyService.validateSpecialtyIds(List.of(1L, 1L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_SPECIALTY));
    }

    @Test
    void rejectsWhenAnySpecialtyDoesNotExist() {
        when(specialtyRepository.countByIdIn(List.of(1L, 99L))).thenReturn(1L);

        assertThatThrownBy(() -> specialtyService.validateSpecialtyIds(List.of(1L, 99L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SPECIALTY_NOT_FOUND));
    }

    @Test
    @DisplayName("학생별 등록 특기 ID를 한 번에 조회해 학생 프로필 ID로 묶는다")
    void groupsSpecialtyIdsByStudent() {
        when(studentSpecialtyRepository.findByStudentProfileIdIn(List.of(7L, 8L, 9L))).thenReturn(List.of(
                StudentSpecialty.create(7L, 21L), StudentSpecialty.create(8L, 11L), StudentSpecialty.create(7L, 11L)));

        assertThat(specialtyService.getSpecialtyIdsByStudentProfileIds(List.of(7L, 8L, 9L)))
                .containsOnly(Map.entry(7L, List.of(21L, 11L)), Map.entry(8L, List.of(11L)));
    }

    @Test
    @DisplayName("대상 학생이 없으면 학생 특기를 조회하지 않는다")
    void skipsStudentSpecialtyQueryWithoutStudents() {
        assertThat(specialtyService.getSpecialtyIdsByStudentProfileIds(List.of())).isEmpty();
        verifyNoInteractions(studentSpecialtyRepository);
    }
}
