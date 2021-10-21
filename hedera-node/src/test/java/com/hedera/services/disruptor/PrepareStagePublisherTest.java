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

import com.hedera.services.utils.PlatformTxnAccessor;
import com.lmax.disruptor.RingBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith({ MockitoExtension.class })
class PrepareStagePublisherTest {
    @Mock RingBuffer<TransactionEvent> ringBuffer;
    @Mock PlatformTxnAccessor txnAccessor;

    PrepareStagePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PrepareStagePublisher(ringBuffer);
    }

    @Test
    void submitSuccessful() {
        TransactionEvent event = new TransactionEvent();

        given(ringBuffer.next()).willReturn(1L);
        given(ringBuffer.get(1L)).willReturn(event);

        // when:
        publisher.submit(txnAccessor);

        // then:
        verify(ringBuffer).publish(1L);
        assertEquals(txnAccessor, event.getAccessor());
        assertFalse(event.isErrored());
    }
}