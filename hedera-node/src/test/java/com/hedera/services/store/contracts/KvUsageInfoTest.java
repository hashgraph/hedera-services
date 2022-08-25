/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KvUsageInfoTest {

    @Test
    void hasExpectedSemantics() {
        final var initUsage = 123;
        final var subject = new KvUsageInfo(initUsage);

        assertEquals(initUsage, subject.pendingUsage());
        assertEquals(0, subject.pendingUsageDelta());

        assertFalse(subject.hasPositiveUsageDelta());
        subject.updatePendingBy(5);
        assertTrue(subject.hasPositiveUsageDelta());
        subject.updatePendingBy(-2);

        assertEquals(initUsage + 3, subject.pendingUsage());
        assertEquals(3, subject.pendingUsageDelta());
    }
}
