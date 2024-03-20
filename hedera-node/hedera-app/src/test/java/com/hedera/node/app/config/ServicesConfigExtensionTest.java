package com.hedera.node.app.config;

import com.swirlds.config.extensions.test.fixtures.ConfigUtils;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ServicesConfigExtensionTest {
    @Test
    void testIfAllConfigDataTypesAreRegistered() {
        // given
        final var allRecordsFound = ConfigUtils.loadAllConfigDataRecords(Set.of("com.hedera"));
        final var extension = new ServicesConfigExtension();

        // when
        final var allConfigDataTypes = extension.getConfigDataTypes();

        // then
        for (final var record : allRecordsFound) {
            if (!allConfigDataTypes.contains(record)) {
                throw new IllegalStateException("Config data type " + record.getSimpleName() + " is not registered");
            }
        }
        Assertions.assertEquals(allRecordsFound.size(), allConfigDataTypes.size());
    }
}