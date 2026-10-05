package com.gakkum.backend.domain.student.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.student.dto.StudentCommandDto.CreateStudentProfileCommand;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    @Transactional
    public Student createStudentProfile(CreateStudentProfileCommand command) {
        Student student = Student.create(
                command.getUserId(),
                command.getUniversity(),
                command.getStudentNumber(),
                command.getMajor(),
                command.getPortfolioUrl(),
                command.getIntroduction(),
                command.getProfileImageUrl());

        return studentRepository.save(student);
    }

    @Transactional(readOnly = true)
    public void validateStudentNumberAvailable(String studentNumber) {
        if (studentRepository.existsByStudentNumber(studentNumber)) {
            throw new BusinessException(ErrorCode.DUPLICATE_STUDENT_NUMBER);
        }
    }

    @Transactional(readOnly = true)
    public boolean existsStudentNumber(String studentNumber) {
        return studentRepository.existsByStudentNumber(studentNumber);
    }

    /**
     * 의뢰에 선택된 학생 프로필 단건 조회
     * @param studentProfileId
     * @return 학생 프로필, 없으면 참조 무결성 오류(500)
     */
    @Transactional(readOnly = true)
    public Student getStudentProfile(Long studentProfileId) {
        if (studentProfileId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return studentRepository.findById(studentProfileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    /**
     * 사용자 ID로 학생 프로필 조회
     * @param userId
     * @return 학생 프로필, 학생이 아니면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<Student> findStudentProfileByUserId(String userId) {
        return studentRepository.findByUserId(userId);
    }

    /**
     * 진행 중(MATCHED)인 의뢰 목록에 대한 학생 정보를 반환
     * @param studentProfileIds
     * @return
     */
    @Transactional(readOnly = true)
    public Map<Long, Student> getStudentProfilesByIds(Collection<Long> studentProfileIds) {

        //
        if (studentProfileIds.stream().anyMatch(id -> id == null)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        Map<Long, Student> studentsById = studentRepository.findAllById(studentProfileIds).stream()
                .collect(Collectors.toMap(Student::getId, student -> student));
        for (Long studentProfileId : studentProfileIds) {
            if (!studentsById.containsKey(studentProfileId)) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
        return studentsById;
    }
}
