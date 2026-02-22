package com.distributed.idgen.ulid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ULIDGenerator")
class ULIDGeneratorTest {

    private ULIDGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ULIDGenerator();
    }

    @Nested
    @DisplayName("Format")
    class Format {

        @Test
        @DisplayName("Should generate a 26-character string")
        void shouldBe26Chars() {
            assertThat(generator.generate()).hasSize(26);
        }

        @Test
        @DisplayName("Should only contain Crockford Base32 characters")
        void shouldOnlyContainCrockfordChars() {
            Set<Character> validChars = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
                    .chars()
                    .mapToObj(c -> (char) c)
                    .collect(Collectors.toSet());

            for (int i = 0; i < 100; i++) {
                String ulid = generator.generate();
                for (char c : ulid.toCharArray()) {
                    assertThat(validChars).contains(c);
                }
            }
        }

        @Test
        @DisplayName("Should not contain ambiguous characters I, L, O, U")
        void shouldNotContainAmbiguousChars() {
            for (int i = 0; i < 100; i++) {
                String ulid = generator.generate();
                assertThat(ulid).doesNotContain("I", "L", "O", "U");
            }
        }
    }

    @Nested
    @DisplayName("Uniqueness")
    class Uniqueness {

        @Test
        @DisplayName("Should generate 10,000 unique ULIDs")
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
        @DisplayName("ULIDs generated in different milliseconds should sort lexicographically")
        void shouldSortLexicographically() throws InterruptedException {
            String ulid1 = generator.generate();
            Thread.sleep(5); // ensure timestamp advances
            String ulid2 = generator.generate();

            assertThat(ulid2.compareTo(ulid1)).isGreaterThan(0);
        }

        @Test
        @DisplayName("The timestamp prefix of the first ULID should not exceed the second")
        void timestampPrefixShouldBeMonotonic() throws InterruptedException {
            String ts1 = generator.generate().substring(0, 10);
            Thread.sleep(5);
            String ts2 = generator.generate().substring(0, 10);

            assertThat(ts2.compareTo(ts1)).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("strategyName should mention ULID")
    void strategyNameShouldMentionULID() {
        assertThat(generator.strategyName()).contains("ULID");
    }
}
