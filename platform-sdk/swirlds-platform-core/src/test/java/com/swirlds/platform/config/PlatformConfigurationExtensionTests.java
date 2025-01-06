/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.config;

import com.swirlds.config.extensions.test.fixtures.ConfigUtils;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PlatformConfigurationExtensionTests {

    @Test
    void testIfAllConfigDataTypesAreRegistered() {
        // given
        final var allRecordsFound = ConfigUtils.loadAllConfigDataRecords(Set.of("com.swirlds", "org.hiero"));
        final var extension = new PlatformConfigurationExtension();

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
