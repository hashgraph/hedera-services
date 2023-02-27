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

package com.swirlds.common.test.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.test.RandomUtils;
import org.junit.jupiter.api.Test;

public class RunningHashTest {
    private static final String HASH_NOT_MATCH_MSG = "Hash doesn't match";

    @Test
    void setHashTest() throws Exception {
        RunningHash runningHash = new RunningHash();
        assertNull(runningHash.getHash(), "after initialization, the Hash should be null");
        Hash hash = RandomUtils.randomHash();
        runningHash.setHash(hash);
        assertEquals(hash, runningHash.getHash(), HASH_NOT_MATCH_MSG);
        assertEquals(hash, runningHash.getFutureHash().get(), HASH_NOT_MATCH_MSG);
    }

    @Test
    void initializeTest() throws Exception {
        Hash hash = RandomUtils.randomHash();
        RunningHash runningHash = new RunningHash(hash);

        assertEquals(hash, runningHash.getHash(), HASH_NOT_MATCH_MSG);
        assertEquals(hash, runningHash.getFutureHash().get(), HASH_NOT_MATCH_MSG);
    }
}
