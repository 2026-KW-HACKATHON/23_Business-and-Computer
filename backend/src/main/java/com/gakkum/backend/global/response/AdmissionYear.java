package com.gakkum.backend.global.response;

/** 다른 사용자에게 보이는 응답에서 학번 전체 대신 입학연도 두 자리만 내린다. 응답 DTO의 from·of 매핑에서만 쓴다. */
public final class AdmissionYear {

    private AdmissionYear() {
    }

    /** 학번 열 자리의 셋째·넷째 자리가 입학연도 두 자리다: 2024402001 → "24". 학번이 없거나 네 자리보다 짧으면 null */
    public static String from(String studentNumber) {
        if (studentNumber == null || studentNumber.length() < 4) {
            return null;
        }
        return studentNumber.substring(2, 4);
    }
}
