/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.platform;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricsEventBusTest {

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Executor executor;

    @Mock
    private Consumer<Object> subscriber;

    @BeforeEach
    void setup() {
        doAnswer(invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return null;
                })
                .when(executor)
                .execute(any());
    }

    @Test
    void testConstructorWithNull() {
        assertThatThrownBy(() -> new MetricsEventBus<>(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSubscribeWithPreviousElements() {
        // given
        final MetricsEventBus<Integer> eventBus = new MetricsEventBus<>(executor);

        // when
        eventBus.subscribe(subscriber, () -> Stream.of(1, 2, 3));

        // then
        verify(subscriber).accept(1);
        verify(subscriber).accept(2);
        verify(subscriber).accept(3);
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    void testSubscribeWithNoPreviousElements() {
        // given
        final MetricsEventBus<Integer> eventBus = new MetricsEventBus<>(executor);

        // when
        eventBus.subscribe(subscriber, Stream::of);

        // then
        verify(subscriber, never()).accept(any());
    }

    @Test
    void testSubscribeWithIllegalArguments() {
        // given
        final MetricsEventBus<Integer> eventBus = new MetricsEventBus<>(executor);

        // then
        assertThatThrownBy(() -> eventBus.subscribe(null, () -> Stream.of(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> eventBus.subscribe(it -> {}, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> eventBus.subscribe(it -> {}, () -> null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSubmit() {
        // given
        final MetricsEventBus<Integer> eventBus = new MetricsEventBus<>(executor);
        eventBus.subscribe(subscriber, Stream::of);

        // when
        eventBus.submit(42);

        // then
        verify(subscriber).accept(42);
        verifyNoMoreInteractions(subscriber);
    }

    @Test
    void testSubmitWithoutSubscribers() {
        // given
        final MetricsEventBus<Integer> eventBus = new MetricsEventBus<>(executor);

        // then
        assertThatCode(() -> eventBus.submit(42)).doesNotThrowAnyException();
    }

    @Test
    void testSubmitWithIllegalArguments() {
        // given
        final MetricsEventBus<Integer> eventBus = new MetricsEventBus<>(executor);

        // then
        assertThatThrownBy(() -> eventBus.submit(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
