package com.gakkum.backend.domain.specialty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

        StudentSpecialty savedSpecialty = specialtyService.addStudentSpecialty(10L, 1L);

        ArgumentCaptor<StudentSpecialty> specialtyCaptor = ArgumentCaptor.forClass(StudentSpecialty.class);
        verify(studentSpecialtyRepository).save(specialtyCaptor.capture());
        assertThat(savedSpecialty).isSameAs(specialtyCaptor.getValue());
        assertThat(savedSpecialty.getStudentProfileId()).isEqualTo(10L);
        assertThat(savedSpecialty.getSpecialtyId()).isEqualTo(1L);
    }

    @Test
    void rejectsMissingSpecialty() {
        when(specialtyRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> specialtyService.addStudentSpecialty(10L, 99L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(studentSpecialtyRepository, never()).save(any(StudentSpecialty.class));
    }
}
