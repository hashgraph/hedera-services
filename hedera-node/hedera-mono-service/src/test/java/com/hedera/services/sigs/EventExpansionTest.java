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
package com.hedera.services.sigs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ServicesState;
import com.hedera.services.sigs.order.SigReqsManager;
import com.hedera.services.txns.prefetch.PrefetchProcessor;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class EventExpansionTest {
    @Mock private Event event;
    @Mock private ServicesState sourceState;
    @Mock private PlatformTxnAccessor txnAccessor;
    @Mock private Cryptography engine;
    @Mock private SigReqsManager sigReqsManager;
    @Mock private ExpandHandleSpan expandHandleSpan;
    @Mock private PrefetchProcessor prefetchProcessor;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private EventExpansion subject;

    @BeforeEach
    void setUp() {
        subject = new EventExpansion(engine, sigReqsManager, expandHandleSpan, prefetchProcessor);
    }

    @Test
    void expandsAndSubmitsSigsForEachTransaction() throws InvalidProtocolBufferException {
        final var n = 3;
        givenNTransactions(n);
        given(expandHandleSpan.track(any())).willReturn(txnAccessor);

        subject.expandAllSigs(event, sourceState);

        verify(prefetchProcessor, times(n)).submit(txnAccessor);
        verify(sigReqsManager, times(n)).expandSigs(sourceState, txnAccessor);
        verify(engine, times(n)).verifyAsync(Collections.emptyList());
    }

    @Test
    void warnsOfNonGrpcTransaction() throws InvalidProtocolBufferException {
        givenNTransactions(1);

        willThrow(InvalidProtocolBufferException.class).given(expandHandleSpan).track(any());

        subject.expandAllSigs(event, sourceState);

        assertThat(
                logCaptor.warnLogs(),
                contains(startsWith("Event contained a non-GRPC transaction")));
    }

    @Test
    void warnsOfExpansionFailure() throws InvalidProtocolBufferException {
        givenNTransactions(1);
        given(expandHandleSpan.track(any())).willReturn(txnAccessor);

        willThrow(IllegalStateException.class).given(sigReqsManager).expandSigs(any(), any());

        subject.expandAllSigs(event, sourceState);

        assertThat(
                logCaptor.warnLogs(),
                contains(
                        startsWith(
                                "Unable to expand signatures, will be verified "
                                        + "synchronously in handleTransaction")));
    }

    @SuppressWarnings("unchecked")
    private void givenNTransactions(final int n) {
        Mockito.doAnswer(
                        invocationOnMock -> {
                            final var consumer =
                                    (Consumer<Transaction>) invocationOnMock.getArgument(0);
                            for (int i = 0; i < n; i++) {
                                consumer.accept(new SwirldTransaction());
                            }
                            return null;
                        })
                .when(event)
                .forEachTransaction(any());
    }
}
