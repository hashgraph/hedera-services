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

import com.hedera.services.txns.PreFetchableTransition;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.utils.PlatformTxnAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith({ MockitoExtension.class })
class PreFetchHandlerTest {
    @Mock TransitionLogicLookup lookup;
    @Mock PlatformTxnAccessor accessor;
    @Mock PreFetchableTransition logic;

    private PreFetchHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PreFetchHandler(0, 2, true, lookup);
    }

    @Test
    void handleSuccessful() {
        final var event = new TransactionEvent();
        event.setAccessor(accessor);
        event.setErrored(false);

        given(lookup.lookupFor(any(), any())).willReturn(Optional.of(logic));

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verify(logic).preFetch(accessor);
        assertNull(event.getAccessor());
    }

    @Test
    void handleNotPrefetchableLogic() {
        final var event = new TransactionEvent();
        event.setAccessor(accessor);
        event.setErrored(false);

        TransitionLogic logic = Mockito.mock(TransitionLogic.class);
        given(lookup.lookupFor(any(), any())).willReturn(Optional.of(logic));

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verifyNoInteractions(logic);
        assertNull(event.getAccessor());
    }

    @Test
    void handleEmptyTransitionLogic() {
        final var event = new TransactionEvent();
        event.setAccessor(accessor);
        event.setErrored(false);

        given(lookup.lookupFor(any(), any())).willReturn(Optional.empty());

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verifyNoInteractions(logic);
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
        verifyNoInteractions(lookup);
        verifyNoInteractions(logic);
        assertNotNull(event.getAccessor());
    }

    @Test
    void handleUnexpectedException() {
        final var event = new TransactionEvent();
        event.setAccessor(accessor);
        event.setErrored(false);

        given(lookup.lookupFor(any(), any())).willThrow(new RuntimeException("oh no"));

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verifyNoInteractions(logic);
        assertNull(event.getAccessor());
    }

    @Test
    void successfulNotLastHandler() {
        final var event = new TransactionEvent();
        event.setAccessor(accessor);
        event.setErrored(false);
        handler = new PreFetchHandler(0, 2, false, lookup);

        given(lookup.lookupFor(any(), any())).willReturn(Optional.of(logic));

        // when:
        handler.onEvent(event, 4, false);

        // then:
        verify(logic).preFetch(accessor);
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
        verifyNoInteractions(lookup);
        assertNull(event.getAccessor());
    }
}
