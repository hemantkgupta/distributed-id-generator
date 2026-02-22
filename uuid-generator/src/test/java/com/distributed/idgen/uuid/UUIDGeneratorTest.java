package com.distributed.idgen.uuid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UUID Generators")
class UUIDGeneratorTest {

    private static final Pattern UUID_PATTERN = Pattern
            .compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    // -----------------------------------------------------------------------
    // UUIDv4
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("UUIDv4Generator")
    class V4Tests {

        private final UUIDv4Generator generator = new UUIDv4Generator();

        @Test
        @DisplayName("Should generate a well-formed UUID string")
        void shouldGenerateValidUUIDFormat() {
            String id = generator.generate();
            assertThat(id).matches(UUID_PATTERN);
        }

        @Test
        @DisplayName("Should report version 4")
        void shouldReportVersion4() {
            String id = generator.generate();
            UUID uuid = UUID.fromString(id);
            assertThat(uuid.version()).isEqualTo(4);
        }

        @Test
        @DisplayName("Should report RFC 4122 variant (2)")
        void shouldHaveCorrectVariant() {
            UUID uuid = UUID.fromString(generator.generate());
            assertThat(uuid.variant()).isEqualTo(2); // RFC 4122
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

        @Test
        @DisplayName("strategyName should mention UUIDv4")
        void strategyNameShouldMentionV4() {
            assertThat(generator.strategyName()).contains("UUIDv4");
        }
    }

    // -----------------------------------------------------------------------
    // UUIDv7
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("UUIDv7Generator")
    class V7Tests {

        private final UUIDv7Generator generator = new UUIDv7Generator();

        @Test
        @DisplayName("Should generate a well-formed UUID string")
        void shouldGenerateValidUUIDFormat() {
            String id = generator.generate();
            assertThat(id).matches(UUID_PATTERN);
        }

        @Test
        @DisplayName("Should encode version 7 in nibble position 12")
        void shouldEncodeVersion7() {
            String id = generator.generate();
            // The 13th character (index 14 with hyphens) is the version nibble
            // Format: xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx
            char versionNibble = id.charAt(14); // position after two groups + hyphens
            assertThat(versionNibble).isEqualTo('7');
        }

        @Test
        @DisplayName("Should encode RFC 4122 variant bits (8, 9, a, or b)")
        void shouldEncodeVariantBits() {
            String id = generator.generate();
            // The first char of the 4th group (index 19) encodes the variant
            char variantNibble = id.charAt(19);
            assertThat(variantNibble).isIn('8', '9', 'a', 'b');
        }

        @Test
        @DisplayName("Should produce time-ordered IDs (prefix increases over time)")
        void shouldProduceTimeOrderedIds() throws InterruptedException {
            String id1 = generator.generate();
            Thread.sleep(2); // ensure different milliseconds
            String id2 = generator.generate();

            // Lexicographic comparison works because the timestamp is at the front
            assertThat(id2.compareTo(id1)).isGreaterThanOrEqualTo(0);
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

        @Test
        @DisplayName("strategyName should mention UUIDv7")
        void strategyNameShouldMentionV7() {
            assertThat(generator.strategyName()).contains("UUIDv7");
        }
    }
}
