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

package com.swirlds.platform.sync.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Peer Agnostic Sync Checks Tests")
class PeerAgnosticSyncChecksTests {
    @Test
    @DisplayName("All checks return true")
    void allTrue() {
        final PeerAgnosticSyncChecks checks = new PeerAgnosticSyncChecks(List.of(() -> true, () -> true, () -> true));

        assertTrue(checks.shouldSync());
    }

    @Test
    @DisplayName("All checks return false")
    void allFalse() {
        final PeerAgnosticSyncChecks checks =
                new PeerAgnosticSyncChecks(List.of(() -> false, () -> false, () -> false));

        assertFalse(checks.shouldSync());
    }

    @Test
    @DisplayName("Some checks return true, some false")
    void mixedReturnValues() {
        final PeerAgnosticSyncChecks checks = new PeerAgnosticSyncChecks(List.of(() -> true, () -> false, () -> true));

        assertFalse(checks.shouldSync());
    }

    @Test
    @DisplayName("No checks are provided")
    void noChecks() {
        final PeerAgnosticSyncChecks checks = new PeerAgnosticSyncChecks(List.of());

        assertTrue(checks.shouldSync());
    }
}
