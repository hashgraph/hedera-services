/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.counters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NoOpObjectCounterTests {

    /**
     * The most important part of the no-op implementation is that it doesn't throw exceptions.
     */
    @Test
    void noThrowingTest() {
        final NoOpObjectCounter counter = NoOpObjectCounter.getInstance();

        counter.onRamp();
        counter.attemptOnRamp();
        counter.forceOnRamp();
        counter.offRamp();
        counter.waitUntilEmpty();
        assertEquals(-1, counter.getCount());
    }
}
