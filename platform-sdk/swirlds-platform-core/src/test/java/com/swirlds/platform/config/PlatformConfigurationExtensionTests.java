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

import org.junit.jupiter.api.Test;

class PlatformConfigurationExtensionTests {

    @Test
    void testIfAllConfigDataTypesAreRegistered() {
        // FIXME TIMO
    }

    @Test
    void requireAlphabetizedConfigRegistration() {
        final PlatformConfigurationExtension extension = new PlatformConfigurationExtension();

        String previous = "";
        for (final Class<?> configDataType : extension.getConfigDataTypes()) {
            final String current = configDataType.getSimpleName();
            if (current.compareTo(previous) < 0) {
                throw new IllegalStateException(
                        "Config data types are not alphabetized: " + previous + " should come after " + current);
            }
            previous = current;
        }
    }
}
