package com.gakkum.backend.domain.specialty.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * 학생별 등록 특기 ID를 한 번에 조회한다.
     * @param studentProfileIds 학생 프로필 ID 목록
     * @return 학생 프로필 ID별 특기 ID 목록, 특기가 없는 학생은 키가 없다
     */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> getSpecialtyIdsByStudentProfileIds(Collection<Long> studentProfileIds) {
        if (studentProfileIds.isEmpty()) {
            return Map.of();
        }
        return studentSpecialtyRepository.findByStudentProfileIdIn(studentProfileIds).stream()
                .collect(Collectors.groupingBy(
                        StudentSpecialty::getStudentProfileId,
                        Collectors.mapping(StudentSpecialty::getSpecialtyId, Collectors.toList())));
    }
}
