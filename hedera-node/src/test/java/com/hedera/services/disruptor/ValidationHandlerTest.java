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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith({ MockitoExtension.class })
class ValidationHandlerTest {
    @Mock Consumer<PlatformTxnAccessor> action1;
    @Mock Consumer<PlatformTxnAccessor> action2;
    @Mock PlatformTxnAccessor accessor;

    private ValidationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ValidationHandler(0, 2, true, action1, action2);
    }

    @Test
    void handleSuccessful() {
        final var event = new TransactionEvent();
        event.setAccessor(accessor);
        event.setErrored(false);

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verify(action1).accept(accessor);
        verify(action2).accept(accessor);
        assertNull(event.getAccessor());
    }

    @Test
    void handleNotOurEvent() {
        final var event = new TransactionEvent();
        event.setAccessor(accessor);
        event.setErrored(false);

        // when:
        handler.onEvent(event, 3, false);

        // then:
        verifyNoInteractions(action1);
        verifyNoInteractions(action2);
        assertNotNull(event.getAccessor());
    }

    @Test
    void successfulNotLastHandler() {
        final var event = new TransactionEvent();
        event.setAccessor(accessor);
        event.setErrored(false);
        handler = new ValidationHandler(0, 2, false, action1, action2);

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verify(action1).accept(accessor);
        verify(action2).accept(accessor);
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
        verifyNoInteractions(action1);
        verifyNoInteractions(action2);
        assertNull(event.getAccessor());
    }
}