/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.consensus;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.events.EventConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RoundCalculationUtilsTest {

    @Test
    void getOldestNonAncientRound() {
        Assertions.assertEquals(
                6,
                RoundCalculationUtils.getOldestNonAncientRound(5, 10),
                "if the latest 5 rounds are ancient, then the oldest non-ancient is 6");
        Assertions.assertEquals(
                EventConstants.MINIMUM_ROUND_CREATED,
                RoundCalculationUtils.getOldestNonAncientRound(10, 5),
                "if no rounds are ancient yet, then the oldest one is the first round");
    }

    @Test
    void getMinGenNonAncientFromSignedState() {
        // generation is equal to round*10
        final Map<Long, Long> map =
                LongStream.range(1, 50).collect(HashMap::new, (m, l) -> m.put(l, l * 10), HashMap::putAll);
        final SignedState signedState = mock(SignedState.class);
        final PlatformMerkleStateRoot state = mock(PlatformMerkleStateRoot.class);
        final PlatformStateModifier platformState = mock(PlatformStateModifier.class);
        when(signedState.getState()).thenReturn(state);
        when(state.getReadablePlatformState()).thenReturn(platformState);

        final AtomicLong lastRoundDecided = new AtomicLong();
        when(signedState.getRound()).thenAnswer(a -> lastRoundDecided.get());
        when(platformState.getRound()).thenAnswer(a -> lastRoundDecided.get());
    }
}
