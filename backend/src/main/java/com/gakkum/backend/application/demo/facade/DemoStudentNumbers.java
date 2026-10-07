package com.gakkum.backend.application.demo.facade;

import java.util.concurrent.ThreadLocalRandom;

import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/** 데모 학생 학번. 방문자 학생과 예시 데이터의 다른 학생이 같은 규칙으로 뽑는다. */
final class DemoStudentNumbers {

    // 실제 학번(입학 연도로 시작)과 겹치지 않게 2099로 시작하는 10자리를 쓴다
    private static final String PREFIX = "2099";
    private static final int ATTEMPTS = 10;

    private DemoStudentNumbers() {
    }

    static String next(StudentService studentService) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String studentNumber = PREFIX + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            if (!studentService.existsStudentNumber(studentNumber)) {
                return studentNumber;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
