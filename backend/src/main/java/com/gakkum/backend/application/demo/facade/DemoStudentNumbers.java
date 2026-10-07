package com.gakkum.backend.application.demo.facade;

import java.util.concurrent.ThreadLocalRandom;

import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/** 데모 학생 학번. 방문자 학생과 예시 데이터의 다른 학생이 같은 규칙으로 뽑는다. */
final class DemoStudentNumbers {

    // 화면은 셋째 · 넷째 자리를 입학 연도로 읽는다(「24학번」). 학번은 서비스 전체에서 겹치면 안 되므로
    // 학과 코드 자리를 0으로 시작하게 해 실제 학번과 겹치기 어렵게 한다: 20 + 입학 연도 두 자리 + 0 + 다섯 자리
    private static final String FORMAT = "20%02d0%05d";
    private static final int ATTEMPTS = 10;

    private DemoStudentNumbers() {
    }

    /** @param admissionYear 입학 연도 두 자리 (24 → 「24학번」) */
    static String next(StudentService studentService, int admissionYear) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            String studentNumber = String.format(FORMAT, admissionYear, ThreadLocalRandom.current().nextInt(100_000));
            if (!studentService.existsStudentNumber(studentNumber)) {
                return studentNumber;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
