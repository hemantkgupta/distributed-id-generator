package com.distributed.idgen.nanoid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NanoIdGenerator")
class NanoIdGeneratorTest {

    private NanoIdGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new NanoIdGenerator();
    }

    @Nested
    @DisplayName("Default configuration")
    class DefaultConfig {

        @Test
        @DisplayName("Should generate a 21-character string by default")
        void shouldBe21Chars() {
            assertThat(generator.generate()).hasSize(21);
        }

        @Test
        @DisplayName("Should only use URL-safe characters from default alphabet")
        void shouldOnlyUseUrlSafeChars() {
            Set<Character> valid = new HashSet<>();
            for (char c : NanoIdGenerator.DEFAULT_ALPHABET.toCharArray()) {
                valid.add(c);
            }
            for (int i = 0; i < 200; i++) {
                for (char c : generator.generate().toCharArray()) {
                    assertThat(valid).contains(c);
                }
            }
        }

        @Test
        @DisplayName("Should generate 10,000 unique IDs")
        void shouldGenerateUniqueIds() {
            int count = 10_000;
            Set<String> ids = new HashSet<>(count);
            for (int i = 0; i < count; i++) {
                ids.add(generator.generate());
            }
            assertThat(ids).hasSize(count);
        }
    }

    @Nested
    @DisplayName("Custom configuration")
    class CustomConfig {

        @Test
        @DisplayName("Should respect custom size")
        void shouldRespectCustomSize() {
            var gen = new NanoIdGenerator(NanoIdGenerator.DEFAULT_ALPHABET, 10);
            assertThat(gen.generate()).hasSize(10);
        }

        @Test
        @DisplayName("Should respect custom alphabet — only letters")
        void shouldRespectCustomAlphabet() {
            String alphabet = "abcdef";
            var gen = new NanoIdGenerator(alphabet, 16);
            String id = gen.generate();
            assertThat(id).hasSize(16).matches("[abcdef]{16}");
        }

        @Test
        @DisplayName("Should work with numeric-only alphabet")
        void shouldWorkWithNumericAlphabet() {
            var gen = new NanoIdGenerator("0123456789", 8);
            assertThat(gen.generate()).matches("[0-9]{8}");
        }

        @Test
        @DisplayName("Should work with single-char alphabet (degenerate case)")
        void shouldWorkWithSingleCharAlphabet() {
            var gen = new NanoIdGenerator("X", 5);
            assertThat(gen.generate()).isEqualTo("XXXXX");
        }
    }

    @Nested
    @DisplayName("Construction validation")
    class ConstructionValidation {

        @Test
        @DisplayName("Should throw for empty alphabet")
        void shouldThrowForEmptyAlphabet() {
            assertThatThrownBy(() -> new NanoIdGenerator("", 21))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should throw for null alphabet")
        void shouldThrowForNullAlphabet() {
            assertThatThrownBy(() -> new NanoIdGenerator(null, 21))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should throw for size < 1")
        void shouldThrowForZeroSize() {
            assertThatThrownBy(() -> new NanoIdGenerator("abc", 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should throw for alphabet > 256 chars")
        void shouldThrowForAlphabetTooLong() {
            String tooLong = "a".repeat(257);
            assertThatThrownBy(() -> new NanoIdGenerator(tooLong, 21))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("strategyName should mention NanoID")
    void strategyNameShouldMentionNanoID() {
        assertThat(generator.strategyName()).contains("NanoID");
    }
}
