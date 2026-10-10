package com.gakkum.backend.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("응답의 입학연도 두 자리")
class AdmissionYearTest {

    @ParameterizedTest
    @CsvSource(value = { "2024402001,24", "2001402001,01", "1999402001,99", "2024,24", "202,NULL", "'',NULL",
            "NULL,NULL" }, nullValues = "NULL")
    @DisplayName("학번의 셋째·넷째 자리만 돌려주고, 학번이 없거나 네 자리보다 짧으면 null을 돌려준다")
    void returnsThirdAndFourthDigits(String studentNumber, String expected) {
        assertThat(AdmissionYear.from(studentNumber)).isEqualTo(expected);
    }
}
