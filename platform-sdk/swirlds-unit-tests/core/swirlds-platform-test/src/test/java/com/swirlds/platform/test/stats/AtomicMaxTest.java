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

package com.swirlds.platform.test.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.platform.stats.AtomicMax;
import org.junit.jupiter.api.Test;

class AtomicMaxTest {
    @Test
    void basic() {
        final long def = 0;
        AtomicMax a = new AtomicMax(def);
        assertEquals(def, a.get(), "The max value should be equal to the initialized value.");

        a.update(1);
        a.update(3);
        a.update(1);

        assertEquals(3, a.get(), "The max should be 3");
        assertEquals(3, a.getAndReset(), "The max should still be 3");
        assertEquals(def, a.get(), "The max after a reset should be set to default");
    }
}
