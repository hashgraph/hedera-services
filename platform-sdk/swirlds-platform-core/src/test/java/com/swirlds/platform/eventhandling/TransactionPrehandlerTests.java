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

package com.swirlds.platform.eventhandling;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DefaultTransactionPrehandler}
 */
class TransactionPrehandlerTests {
    @Test
    @DisplayName("Normal operation")
    void normalOperation() {
        final Random random = RandomUtils.getRandomPrintSeed();

        final AtomicBoolean returnValidState = new AtomicBoolean(false);
        final AtomicBoolean stateRetrievalAttempted = new AtomicBoolean(false);

        final AtomicBoolean stateClosed = new AtomicBoolean(false);
        final ReservedSignedState state = mock(ReservedSignedState.class);
        doAnswer(invocation -> {
                    assertFalse(stateClosed::get);
                    stateClosed.set(true);
                    return null;
                })
                .when(state)
                .close();

        final SignedStateNexus latestImmutableStateNexus = mock(SignedStateNexus.class);
        // return null until returnValidState is set to true. keep track of when the first state retrieval is attempted,
        // so we can assert that prehandle hasn't happened before the state is available
        when(latestImmutableStateNexus.getState(any())).thenAnswer(i -> {
            stateRetrievalAttempted.set(true);
            return returnValidState.get() ? state : null;
        });

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final TransactionPrehandler transactionPrehandler =
                new DefaultTransactionPrehandler(platformContext, () -> latestImmutableStateNexus.getState("test"));

        final PlatformEvent platformEvent = new TestingEventBuilder(random).build();

        final AtomicBoolean prehandleCompleted = new AtomicBoolean(false);
        new Thread(() -> {
                    platformEvent.awaitPrehandleCompletion();
                    prehandleCompleted.set(true);
                })
                .start();

        new Thread(() -> transactionPrehandler.prehandleApplicationTransactions(platformEvent)).start();

        assertEventuallyTrue(stateRetrievalAttempted::get, Duration.ofSeconds(1), "state retrieval wasn't attempted");
        assertFalse(prehandleCompleted::get, "prehandle completed before state was available");
        returnValidState.set(true);

        assertEventuallyTrue(prehandleCompleted::get, Duration.ofSeconds(1), "prehandle didn't complete");
        assertEventuallyTrue(stateClosed::get, Duration.ofSeconds(1), "state wasn't closed");
    }
}
