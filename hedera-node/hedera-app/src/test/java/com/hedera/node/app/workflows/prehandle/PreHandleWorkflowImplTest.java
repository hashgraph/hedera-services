/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.workflows.prehandle;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.hedera.node.app.ServicesAccessor;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.ingest.IngestChecker;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleWorkflowImplTest {

    @Mock private ExecutorService executorService;
    @Mock private CryptoService cryptoService;
    @Mock private FileService fileService;
    @Mock private TokenService tokenService;
    @Mock private IngestChecker ingestChecker;
    private ServicesAccessor servicesAccessor;

    private PreHandleWorkflowImpl workflow;

    @BeforeEach
    void setup() {
        servicesAccessor = new ServicesAccessor(cryptoService, fileService, tokenService);
        workflow = new PreHandleWorkflowImpl(executorService, servicesAccessor, ingestChecker);
    }

    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(null, servicesAccessor, ingestChecker))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(executorService, null, ingestChecker))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(executorService, servicesAccessor, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testStartWithIllegalParameters() {
        // given
        final HederaState state = mock(HederaState.class);
        final Event event = mock(Event.class);

        // then
        assertThatThrownBy(() -> workflow.start(null, event))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> workflow.start(state, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testStartEventWithNoTransactions() {
        // given
        final HederaState state = mock(HederaState.class);
        final Event event = mock(Event.class);
        when(event.transactionIterator()).thenReturn(Collections.emptyIterator());

        // when
        assertThatCode(() -> workflow.start(state, event)).doesNotThrowAnyException();
    }

    @Test
    void testStartEventWithTwoTransactions() {
        // given
        final HederaState state = mock(HederaState.class);
        final Event event = mock(Event.class);
        final Transaction transaction1 = mock(SwirldTransaction.class);
        final Transaction transaction2 = mock(SwirldTransaction.class);
        final Iterator<Transaction> iterator = List.of(transaction1, transaction2).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        // when
        workflow.start(state, event);

        // then
        verify(transaction1).setMetadata(any());
        verify(transaction2).setMetadata(any());
    }

    @Test
    void testUnchangedStateDoesNotRegenerateHandlers() {
        // given
        var state = mock(HederaState.class);
        final Event event = mock(Event.class);
        final Transaction transaction = mock(SwirldTransaction.class);
        final Iterator<Transaction> iterator = List.of(transaction).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        // when
        workflow.start(state, event);
        workflow.start(state, event);

        // then
        verify(cryptoService, times(1)).createQueryHandler(any());
    }

    @Test
    void testChangedStateDoesRegenerateHandlers() {
        // given
        var state1 = mock(HederaState.class);
        var state2 = mock(HederaState.class);
        final Event event = mock(Event.class);
        final Transaction transaction = mock(SwirldTransaction.class);
        final Iterator<Transaction> iterator = List.of(transaction).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        // when
        workflow.start(state1, event);
        workflow.start(state2, event);

        // then
        verify(cryptoService, times(2)).createQueryHandler(any());
    }
}
