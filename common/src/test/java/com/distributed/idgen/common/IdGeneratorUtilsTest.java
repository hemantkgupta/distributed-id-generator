package com.distributed.idgen.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("IdGeneratorUtils")
class IdGeneratorUtilsTest {

    @Nested
    @DisplayName("validateNodeId")
    class ValidateNodeId {

        @Test
        @DisplayName("Should pass when id is within valid range")
        void shouldPassForValidId() {
            assertThatNoException()
                    .isThrownBy(() -> IdGeneratorUtils.validateNodeId(0, 1023, "workerId"));
            assertThatNoException()
                    .isThrownBy(() -> IdGeneratorUtils.validateNodeId(512, 1023, "workerId"));
            assertThatNoException()
                    .isThrownBy(() -> IdGeneratorUtils.validateNodeId(1023, 1023, "workerId"));
        }

        @Test
        @DisplayName("Should throw when id is negative")
        void shouldThrowForNegativeId() {
            assertThatThrownBy(() -> IdGeneratorUtils.validateNodeId(-1, 1023, "workerId"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("workerId");
        }

        @Test
        @DisplayName("Should throw when id exceeds max value")
        void shouldThrowWhenIdExceedsMax() {
            assertThatThrownBy(() -> IdGeneratorUtils.validateNodeId(1024, 1023, "workerId"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1023");
        }
    }

    @Nested
    @DisplayName("toHexString")
    class ToHexString {

        @Test
        @DisplayName("Should convert bytes to lowercase hex")
        void shouldConvertBytesToHex() {
            byte[] bytes = { 0x0F, (byte) 0xFF, 0x00, (byte) 0xAB };
            String hex = IdGeneratorUtils.toHexString(bytes);
            assertThat(hex).isEqualTo("0fff00ab");
        }

        @Test
        @DisplayName("Should return empty string for empty byte array")
        void shouldReturnEmptyStringForEmptyBytes() {
            assertThat(IdGeneratorUtils.toHexString(new byte[0])).isEmpty();
        }
    }
}
