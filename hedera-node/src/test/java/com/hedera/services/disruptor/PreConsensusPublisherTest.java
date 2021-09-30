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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.lmax.disruptor.RingBuffer;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith({ MockitoExtension.class })
public class PreConsensusPublisherTest {
    @Mock RingBuffer<TransactionEvent> ringBuffer;
    @Mock ExpandHandleSpan expandHandleSpan;
    @Mock PlatformTxnAccessor txnAccessor;
    @Mock SwirldTransaction transaction;

    PreConsensusPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PreConsensusPublisher(ringBuffer, expandHandleSpan);
    }

    @Test
    void submitSuccessful() throws InvalidProtocolBufferException {
        TransactionEvent event = new TransactionEvent();
        Instant now = Instant.now();

        given(ringBuffer.next()).willReturn(1L);
        given(ringBuffer.get(1L)).willReturn(event);
        given(expandHandleSpan.accessorFor(transaction)).willReturn(txnAccessor);

        // when:
        publisher.submit(transaction);

        // then:
        verify(ringBuffer).publish(1L);
        assertEquals(123, event.getSubmittingMember());
        assertEquals(now, event.getCreationTime());
        assertEquals(txnAccessor, event.getAccessor());
        assertFalse(event.isErrored());
    }

    @Test
    void gRPCTransactionError() throws InvalidProtocolBufferException {
        TransactionEvent event = new TransactionEvent();
        Instant now = Instant.now();

        given(ringBuffer.next()).willReturn(1L);
        given(ringBuffer.get(1L)).willReturn(event);
        given(expandHandleSpan.accessorFor(transaction)).willThrow(new InvalidProtocolBufferException("bad"));

        // when:
        publisher.submit(transaction);

        // then:
        verify(ringBuffer).publish(1L);
        assertTrue(event.isErrored());
    }
}