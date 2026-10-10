package com.offertracker;

import com.offertracker.service.LocalDeduplicationFilter;
import com.offertracker.service.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AiInfrastructureStrategyTest {
    @Test void snowflakeIdsAreUniqueAndPositive() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 5000; i++) assertTrue(ids.add(generator.nextId()));
    }

    @Test void localDeduplicationFilterIsAHintWithStableMembership() {
        LocalDeduplicationFilter filter = new LocalDeduplicationFilter();
        assertFalse(filter.mightContain("fresh-key"));
        filter.put("fresh-key");
        assertTrue(filter.mightContain("fresh-key"));
    }
}
