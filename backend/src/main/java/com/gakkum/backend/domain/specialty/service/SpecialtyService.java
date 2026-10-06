package com.gakkum.backend.domain.specialty.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.specialty.dto.SpecialtyCommandDto.AddStudentSpecialtyCommand;
import com.gakkum.backend.domain.specialty.entity.Specialty;
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

    /**
     * 학생이 등록한 특기를 모두 지운다.
     * 같은 특기를 곧바로 다시 등록해도 (학생, 특기) 고유 제약에 걸리지 않도록 삭제를 바로 DB에 반영한다.
     */
    @Transactional
    public void deleteStudentSpecialties(Long studentProfileId) {
        studentSpecialtyRepository.deleteByStudentProfileId(studentProfileId);
        studentSpecialtyRepository.flush();
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

    /** 데모 예시 데이터에 쓸 특기. ID가 작은 순서로 limit개까지 고르고, 특기 기준 데이터가 없으면 비어 있다. */
    @Transactional(readOnly = true)
    public List<Long> getFirstSpecialtyIds(int limit) {
        return specialtyRepository.findAllByOrderByIdAsc().stream()
                .limit(limit)
                .map(Specialty::getId)
                .toList();
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
