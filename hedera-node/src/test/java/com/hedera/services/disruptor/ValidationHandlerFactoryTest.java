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

import com.hedera.services.sigs.SignatureExpander;
import com.hedera.services.state.logic.TxnIndependentValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({ MockitoExtension.class })
class ValidationHandlerFactoryTest {
    @Mock SignatureExpander expander;
    @Mock TxnIndependentValidator validator;
    @Mock PlatformTxnAccessor accessor;

    private ValidationHandlerFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ValidationHandlerFactory(expander, validator);
    }

    @Test
    void createForPreConsensus() {
        final var event = new TransactionEvent();
        event.setAccessor(accessor);

        final var handler = factory.createForPreConsensus(0, 1, true);

        // when:
        handler.onEvent(event, 1, false);

        // then:
        verify(expander).accept(accessor);
        verify(validator).accept(accessor);
    }

    @Test
    void createForConsensus() {
        final var event = new TransactionEvent();
        event.setAccessor(accessor);

        final var handler = factory.createForConsensus(0, 1, true);

        // when:
        handler.onEvent(event, 1, false);

        // then:
        verify(expander, never()).accept(accessor);
        verify(validator).accept(accessor);
    }
}
