package com.distributed.idgen.objectid;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectIdGeneratorTest {

    @Test
    void testGenerateObjectIdFormat() {
        ObjectIdGenerator generator = new ObjectIdGenerator();
        String id = generator.generate();

        assertThat(id).isNotNull();
        assertThat(id).hasSize(24);
        assertThat(id).matches("^[0-9a-f]{24}$");
    }

    @Test
    void testStrategyName() {
        ObjectIdGenerator generator = new ObjectIdGenerator();
        assertThat(generator.strategyName()).contains("MongoDB");
    }

    @Test
    void testUniqueness() {
        ObjectIdGenerator generator = new ObjectIdGenerator();
        Set<String> ids = new HashSet<>();
        
        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.generate());
        }

        assertThat(ids).hasSize(10_000);
    }
}
