package com.gakkum.backend.domain.specialty.service;

import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.specialty.dto.SpecialtyCommandDto.AddStudentSpecialtyCommand;
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
    public StudentSpecialty addStudentSpecialty(AddStudentSpecialtyCommand command) {
        if (!specialtyRepository.existsById(command.getSpecialtyId())) {
            throw new BusinessException(ErrorCode.SPECIALTY_NOT_FOUND);
        }

        StudentSpecialty studentSpecialty = StudentSpecialty.create(
                command.getStudentProfileId(),
                command.getSpecialtyId());

        return studentSpecialtyRepository.save(studentSpecialty);
    }

    @Transactional(readOnly = true)
    public void validateSpecialtyIds(List<Long> specialtyIds) {
        if (new HashSet<>(specialtyIds).size() != specialtyIds.size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SPECIALTY);
        }

        if (!specialtyIds.isEmpty() && specialtyRepository.countByIdIn(specialtyIds) != specialtyIds.size()) {
            throw new BusinessException(ErrorCode.SPECIALTY_NOT_FOUND);
        }
    }
}
