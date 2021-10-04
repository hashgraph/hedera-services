package com.hedera.services.disruptor;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.logic.StandardProcessLogic;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.swirlds.common.SwirldDualState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith({ MockitoExtension.class })
class ExecutionHandlerTest {
    @Mock StandardProcessLogic processLogic;
    @Mock PlatformTxnAccessor accessor;
    @Mock DualStateAccessor dualStateAccessor;
    @Mock SwirldDualState dualState;
    @Mock Latch latch;

    private ExecutionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExecutionHandler(true, processLogic, dualStateAccessor, latch);
    }

    @Test
    void handleSuccessful() {
        final var now = Instant.now();
        final var event = new TransactionEvent();
        event.setSubmittingMember(123);
        event.setAccessor(accessor);
        event.setDualState(dualState);
        event.setConsensusTime(now);
        event.setErrored(false);

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verify(dualStateAccessor).setDualState(dualState);
        verify(processLogic).incorporateConsensusTxn(accessor, now, 123);
        verifyNoInteractions(latch);
        assertNull(event.getAccessor());
    }

    @Test
    void successfulNotLastHandler() {
        final var now = Instant.now();
        final var event = new TransactionEvent();
        event.setSubmittingMember(123);
        event.setAccessor(accessor);
        event.setDualState(dualState);
        event.setConsensusTime(now);
        event.setErrored(false);

        handler = new ExecutionHandler(false, processLogic, dualStateAccessor, latch);

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verify(dualStateAccessor).setDualState(dualState);
        verify(processLogic).incorporateConsensusTxn(accessor, now, 123);
        verifyNoInteractions(latch);
        assertNotNull(event.getAccessor());
    }

    @Test
    void skipEventWhenErrored() {
        final var event = new TransactionEvent();
        event.setAccessor(accessor);
        event.setErrored(true);

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verifyNoInteractions(dualStateAccessor);
        verifyNoInteractions(processLogic);
        verifyNoInteractions(latch);
        assertNull(event.getAccessor());
    }

    @Test
    void errorDuringHandle() {
        final var now = Instant.now();
        final var event = new TransactionEvent();
        event.setSubmittingMember(123);
        event.setAccessor(accessor);
        event.setDualState(dualState);
        event.setConsensusTime(now);
        event.setErrored(false);

        doThrow(new RuntimeException("bad")).when(processLogic).incorporateConsensusTxn(accessor, now, 123);

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verifyNoInteractions(latch);
        assertNull(event.getAccessor());
    }

    @Test
    void handleLastEvent() {
        final var event = new TransactionEvent();
        event.setLast(true);

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verify(latch).countdown();
        assertNull(event.getAccessor());
    }
}