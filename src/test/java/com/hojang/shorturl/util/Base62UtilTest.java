package com.hojang.shorturl.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Base62UtilTest {

    private final Base62Util base62Util = new Base62Util();

    @Test
    @DisplayName("0을 인코딩하면 첫 번째 문자인 '0'이 나와야 한다")
    void encodeZeroTest() {
        assertThat(base62Util.encode(0)).isEqualTo("0");
    }

    @Test
    @DisplayName("초기값(약 568억)을 인코딩하면 정확히 7자리 문자열('1000000')이 나와야 한다")
    void encodeInitialValueTest() {
        // Given
        long initialValue = 56_800_235_584L; // 62^6

        // When
        String result = base62Util.encode(initialValue);

        // Then
        assertThat(result).isEqualTo("1000000"); // 7자리 시작점 확인
    }

    @Test
    @DisplayName("매우 큰 숫자도 에러 없이 인코딩되어야 한다")
    void encodeLargeValueTest() {
        assertThat(base62Util.encode(Long.MAX_VALUE)).isNotNull();
    }
}