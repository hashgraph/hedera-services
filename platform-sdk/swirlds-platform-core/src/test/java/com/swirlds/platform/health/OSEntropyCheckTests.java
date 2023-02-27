/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.health;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.health.entropy.OSEntropyCheck;
import org.junit.jupiter.api.Test;

class OSEntropyCheckTests {

    /**
     * All systems this test runs on should have an entropy generator, so the test should always pass
     */
    @Test
    void basicTest() {
        final OSEntropyCheck.Report report =
                assertDoesNotThrow(() -> OSEntropyCheck.execute(), "Check should not throw");
        assertTrue(report.success(), "Check should succeed");
        assertNotNull(report.elapsedNanos(), "Elapsed nanos should not be null");
        assertTrue(report.elapsedNanos() > 0, "Elapsed nanos should have a positive value");
        assertNotNull(report.randomLong(), "A random long should have been generated");
    }
}
