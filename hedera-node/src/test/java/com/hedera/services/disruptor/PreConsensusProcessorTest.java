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

import com.hedera.services.context.properties.NodeLocalProperties;
import com.lmax.disruptor.RingBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

@ExtendWith({ MockitoExtension.class })
@MockitoSettings(strictness = Strictness.LENIENT)
class PreConsensusProcessorTest {
    @Mock
    NodeLocalProperties properties;
    @Mock
    ValidationHandlerFactory handlerFactory;
    @Mock
    PreConsensusPublisher.Factory publisherFactory;

    PreConsensusProcessor processor;
    @Captor
    ArgumentCaptor<RingBuffer<TransactionEvent>> captor;

    @AfterEach
    void teardown() {
        processor.shutdown();
    }

    @Test
    void createSuccessful() {
        given(properties.preConsensusRingBufferPower()).willReturn(2);
        given(properties.preConsensusValidationHandlerCount()).willReturn(2);
        given(handlerFactory.createForPreConsensus(0, 2, true)).willReturn(Mockito.mock(ValidationHandler.class));
        given(handlerFactory.createForPreConsensus(1, 2, true)).willReturn(Mockito.mock(ValidationHandler.class));
        given(publisherFactory.create(captor.capture())).willReturn(Mockito.mock(PreConsensusPublisher.class));

        // when:
        processor = new PreConsensusProcessor(properties, handlerFactory, publisherFactory);

        // then:
        verify(handlerFactory).createForPreConsensus(0, 2, true);
        verify(handlerFactory).createForPreConsensus(1, 2, true);
        verify(publisherFactory).create(Mockito.any(RingBuffer.class));
        assertEquals(4, captor.getValue().getBufferSize());
    }

    @Test
    void createWithInvalidParameters() {
        given(properties.preConsensusRingBufferPower()).willReturn(-1);
        given(properties.preConsensusValidationHandlerCount()).willReturn(-1);
        given(handlerFactory.createForPreConsensus(0, 1, true)).willReturn(Mockito.mock(ValidationHandler.class));
        given(publisherFactory.create(captor.capture())).willReturn(Mockito.mock(PreConsensusPublisher.class));

        // when:
        processor = new PreConsensusProcessor(properties, handlerFactory, publisherFactory);

        // then:
        verify(handlerFactory).createForPreConsensus(0, 1, true);
        verify(publisherFactory).create(Mockito.any(RingBuffer.class));
        assertEquals(16384, captor.getValue().getBufferSize());
    }
}