package com.gakkum.backend.domain.specialty.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.specialty.entity.StudentSpecialty;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.repository.StudentSpecialtyRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpecialtyService {

    private final SpecialtyRepository specialtyRepository;
    private final StudentSpecialtyRepository studentSpecialtyRepository;

    @Transactional
    public StudentSpecialty addStudentSpecialty(Long studentProfileId, Long specialtyId) {
        if (!specialtyRepository.existsById(specialtyId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        StudentSpecialty studentSpecialty = StudentSpecialty.create(studentProfileId, specialtyId);

        return studentSpecialtyRepository.save(studentSpecialty);
    }
}
