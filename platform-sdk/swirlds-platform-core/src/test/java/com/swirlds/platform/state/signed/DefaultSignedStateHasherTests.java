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

package com.swirlds.platform.state.signed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.wiring.components.StateAndRound;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultSignedStateHasher}
 */
public class DefaultSignedStateHasherTests {
    @Test
    @DisplayName("Normal operation")
    void normalOperation() {
        // mock metrics
        final RunningAverageMetric hashingTimeMetric = mock(RunningAverageMetric.class);
        final SignedStateMetrics signedStateMetrics = mock(SignedStateMetrics.class);
        when(signedStateMetrics.getSignedStateHashingTimeMetric()).thenReturn(hashingTimeMetric);

        // create the hasher
        final AtomicBoolean fatalErrorConsumer = new AtomicBoolean();
        final SignedStateHasher hasher =
                new DefaultSignedStateHasher(signedStateMetrics, (a, b, c) -> fatalErrorConsumer.set(true));

        // mock a state
        final SignedState signedState = mock(SignedState.class);
        final ReservedSignedState reservedSignedState = mock(ReservedSignedState.class);
        when(reservedSignedState.get()).thenReturn(signedState);

        final StateAndRound stateAndRound = new StateAndRound(reservedSignedState, mock(ConsensusRound.class));

        // do the test
        final StateAndRound result = hasher.hashState(stateAndRound);
        assertNotEquals(null, result, "The hasher should return a new StateAndRound");

        // hashing time metric should get updated
        verify(signedStateMetrics).getSignedStateHashingTimeMetric();
        assertFalse(fatalErrorConsumer.get(), "There should be no fatal errors");
    }
}
