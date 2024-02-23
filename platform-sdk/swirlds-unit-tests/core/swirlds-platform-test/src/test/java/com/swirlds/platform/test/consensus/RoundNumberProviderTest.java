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

package com.swirlds.platform.test.consensus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.platform.consensus.RoundNumberProvider;
import org.junit.jupiter.api.Test;

class RoundNumberProviderTest {
    @Test
    void basic() {
        final long ninRound = 100;
        final RoundNumberProvider rnp = new RoundNumberProvider() {
            @Override
            public long getFameDecidedBelow() {
                return 0;
            }

            @Override
            public long getMaxRound() {
                return 0;
            }

            @Override
            public long getMinRound() {
                return ninRound;
            }
        };
        assertEquals(ninRound - 1, rnp.getDeleteRound(), "delete round should be the one before min round");
    }
}
