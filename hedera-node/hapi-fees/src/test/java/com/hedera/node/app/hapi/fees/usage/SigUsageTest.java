/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.fees.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class SigUsageTest {
    @Test
    void gettersWork() {
        final SigUsage one = new SigUsage(3, 256, 2);
        assertEquals(3, one.numSigs());
        assertEquals(256, one.sigsSize());
        assertEquals(2, one.numPayerKeys());
    }

    @Test
    void sigUsageObjectContractWorks() {
        final SigUsage one = new SigUsage(3, 256, 2);
        final SigUsage two = new SigUsage(3, 256, 2);
        final SigUsage three = new SigUsage(4, 256, 2);

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertNotEquals(one, three);
        assertEquals(one, two);

        assertEquals(one.hashCode(), two.hashCode());
        assertNotEquals(one.hashCode(), three.hashCode());

        assertEquals(one.toString(), two.toString());
        assertNotEquals(one.toString(), three.toString());
    }
}
