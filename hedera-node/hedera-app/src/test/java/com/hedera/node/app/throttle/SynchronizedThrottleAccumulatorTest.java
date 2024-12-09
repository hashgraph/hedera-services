/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.throttle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.workflows.TransactionInfo;
import com.swirlds.state.State;
import java.time.InstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SynchronizedThrottleAccumulatorTest {

    @Mock
    private ThrottleAccumulator throttleAccumulator;

    @Mock
    private TransactionInfo transactionInfo;

    private final InstantSource instantSource = InstantSource.system();

    SynchronizedThrottleAccumulator subject;

    @BeforeEach
    void setUp() {
        subject = new SynchronizedThrottleAccumulator(instantSource, throttleAccumulator);
    }

    @Test
    void verifyCheckAndEnforceThrottleIsCalled() {
        // given
        final var state = mock(State.class);

        // when
        subject.shouldThrottle(transactionInfo, state);

        // then
        verify(throttleAccumulator, times(1)).checkAndEnforceThrottle(eq(transactionInfo), any(), eq(state));
    }

    @Test
    void verifyCheckAndEnforceThrottleQueryIsCalled() {
        // given
        final var query = mock(Query.class);
        final var state = mock(State.class);
        final var accountID = mock(AccountID.class);

        // when
        subject.shouldThrottle(HederaFunctionality.CONTRACT_CREATE, query, state, accountID);

        // then
        verify(throttleAccumulator, times(1))
                .checkAndEnforceThrottle(
                        eq(HederaFunctionality.CONTRACT_CREATE), any(), eq(query), eq(state), eq(accountID));
    }
}
