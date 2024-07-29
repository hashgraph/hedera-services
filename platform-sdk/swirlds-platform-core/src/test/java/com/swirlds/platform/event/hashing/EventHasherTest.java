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

package com.swirlds.platform.event.hashing;

import static org.junit.jupiter.api.Assertions.*;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EventHasherTest {
    /**
     * Tests that different hashing methods produce the same hash.
     */
    @Test
    void objHashingEquivalenceTest() {
        final Random random = Randotron.create();

        final PlatformEvent event = new TestingEventBuilder(random)
                .setAppTransactionCount(2)
                .setSystemTransactionCount(2)
                .setSelfParent(new TestingEventBuilder(random).build())
                .setOtherParent(new TestingEventBuilder(random).build())
                .build();

        new PbjBytesHasher().hashEvent(event);
        final Hash bytesHash = event.getHash();
        event.invalidateHash();
        new PbjStreamHasher().hashEvent(event);

        assertEquals(bytesHash, event.getHash(), "PBJ bytes hasher and PBJ stream hasher should produce the same hash");
    }
}
