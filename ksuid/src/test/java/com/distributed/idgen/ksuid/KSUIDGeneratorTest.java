package com.distributed.idgen.ksuid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

@DisplayName("KSUIDGenerator")
class KSUIDGeneratorTest {

    private KSUIDGenerator generator;

    // Valid Base62 characters
    private static final Pattern BASE62_PATTERN = Pattern.compile("[0-9A-Za-z]{27}");

    @BeforeEach
    void setUp() {
        generator = new KSUIDGenerator();
    }

    @Nested
    @DisplayName("Format")
    class Format {

        @Test
        @DisplayName("Should generate a 27-character string")
        void shouldBe27Chars() {
            assertThat(generator.generate()).hasSize(27);
        }

        @Test
        @DisplayName("Should only contain Base62 characters")
        void shouldOnlyContainBase62Chars() {
            for (int i = 0; i < 100; i++) {
                assertThat(generator.generate()).matches(BASE62_PATTERN);
            }
        }
    }

    @Nested
    @DisplayName("Uniqueness")
    class Uniqueness {

        @Test
        @DisplayName("Should generate 10,000 unique KSUIDs")
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
    @DisplayName("Ordering")
    class Ordering {

        @Test
        @DisplayName("KSUIDs generated apart in time should sort correctly")
        void shouldSortLexicographically() throws InterruptedException {
            String first = generator.generate();
            Thread.sleep(1100); // KSUID uses 1-second granularity
            String second = generator.generate();

            assertThat(second.compareTo(first)).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Base62 encoding")
    class Base62EncodingTest {

        @Test
        @DisplayName("Should encode all-zero bytes as all zeros")
        void shouldEncodeZeroBytes() {
            String encoded = KSUIDGenerator.base62Encode(new byte[20]);
            assertThat(encoded).hasSize(27).matches("[0]{27}");
        }

        @Test
        @DisplayName("Encoding should always produce exactly 27 characters")
        void shouldAlwaysBe27Chars() {
            byte[] bytes = new byte[20];
            for (int i = 0; i < 20; i++) {
                bytes[i] = (byte) 0xFF;
            }
            assertThat(KSUIDGenerator.base62Encode(bytes)).hasSize(27);
        }
    }

    @Test
    @DisplayName("strategyName should mention KSUID")
    void strategyNameShouldMentionKSUID() {
        assertThat(generator.strategyName()).contains("KSUID");
    }
}
