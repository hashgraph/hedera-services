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
package com.hedera.services.api.implementation.workflows.prehandle.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.hedera.node.app.service.token.CryptoQueryHandler;
import com.hedera.services.api.implementation.workflows.ingest.IngestChecker;
import com.hedera.services.api.implementation.workflows.prehandle.PreHandleDispatcher;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PreHandleWorkflowImplTest {

    @SuppressWarnings("unchecked")
    @Test
    void testConstructorWithIllegalParameters() {
        // given
        final ExecutorService exe = mock(ExecutorService.class);
        final Supplier<CryptoQueryHandler> query = mock(Supplier.class);
        final IngestChecker ingestChecker = mock(IngestChecker.class);
        final PreHandleDispatcher dispatcher = mock(PreHandleDispatcher.class);

        // then
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(null, query, ingestChecker, dispatcher))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(exe, null, ingestChecker, dispatcher))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(exe, query, null, dispatcher))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(exe, query, ingestChecker, null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testStartWithIllegalParameters() {
        // given
        final ExecutorService exe = mock(ExecutorService.class);
        final Supplier<CryptoQueryHandler> query = mock(Supplier.class);
        final IngestChecker ingestChecker = mock(IngestChecker.class);
        final PreHandleDispatcher dispatcher = mock(PreHandleDispatcher.class);
        final PreHandleWorkflowImpl workflow =
                new PreHandleWorkflowImpl(exe, query, ingestChecker, dispatcher);

        // then
        assertThatThrownBy(() -> workflow.start(null)).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testStartEventWithNoTransactions() {
        // given
        final ExecutorService exe = mock(ExecutorService.class);
        final Supplier<CryptoQueryHandler> query = mock(Supplier.class);
        final IngestChecker ingestChecker = mock(IngestChecker.class);
        final PreHandleDispatcher dispatcher = mock(PreHandleDispatcher.class);
        final PreHandleWorkflowImpl workflow =
                new PreHandleWorkflowImpl(exe, query, ingestChecker, dispatcher);
        final Event event = mock(Event.class);
        when(event.transactionIterator()).thenReturn(Collections.emptyIterator());

        // when
        workflow.start(event);

        // then
        verify(exe, never()).submit(any(Callable.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testStartEventWithTwoTransactions() {
        // given
        final ExecutorService exe = mock(ExecutorService.class);
        final Supplier<CryptoQueryHandler> query = mock(Supplier.class);
        final IngestChecker ingestChecker = mock(IngestChecker.class);
        final PreHandleDispatcher dispatcher = mock(PreHandleDispatcher.class);
        final PreHandleWorkflowImpl workflow =
                new PreHandleWorkflowImpl(exe, query, ingestChecker, dispatcher);
        final Event event = mock(Event.class);

        final Transaction transaction1 = new SwirldTransaction();
        final Transaction transaction2 = new SwirldTransaction();
        final Iterator<Transaction> iterator = List.of(transaction1, transaction2).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        // when
        workflow.start(event);

        // then
        verify(exe, times(2)).submit(any(Callable.class));
    }
}
