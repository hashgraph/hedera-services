package com.hedera.services.txns.prefetch;

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
import com.hedera.services.txns.PreFetchableTransition;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.txns.prefetch.PrefetchProcessor.MINIMUM_QUEUE_CAPACITY;
import static com.hedera.services.txns.prefetch.PrefetchProcessor.MINIMUM_THREAD_POOL_SIZE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
@MockitoSettings(strictness = Strictness.LENIENT)
class PrefetchProcessorTest {
    @Mock NodeLocalProperties properties;
    @Mock TransitionLogicLookup lookup;
    @Mock PlatformTxnAccessor accessor;
    @Mock PreFetchableTransition logic;

    @LoggingTarget
    LogCaptor logCaptor;
    @LoggingSubject
    PrefetchProcessor processor;

    @AfterEach
    void teardown() {
        processor.shutdown();
    }

    @Test
    void createSuccessful() {
        given(properties.prefetchQueueCapacity()).willReturn(MINIMUM_QUEUE_CAPACITY + 1);
        given(properties.prefetchThreadPoolSize()).willReturn(MINIMUM_THREAD_POOL_SIZE + 1);

        processor = new PrefetchProcessor(properties, lookup) {
            @Override
            ExecutorService createExecutorService(int threadPoolSize, BlockingQueue<Runnable> queue) {
                assertEquals(MINIMUM_QUEUE_CAPACITY + 1, queue.remainingCapacity());
                assertEquals(MINIMUM_THREAD_POOL_SIZE + 1, threadPoolSize);

                return new ThreadPoolExecutor(
                        threadPoolSize,
                        threadPoolSize,
                        0L,
                        TimeUnit.MILLISECONDS,
                        queue
                );
            }
        };
    }

    @Test
    void createWithInvalidParameters() {
        given(properties.prefetchQueueCapacity()).willReturn(2);
        given(properties.prefetchThreadPoolSize()).willReturn(1);

        processor = new PrefetchProcessor(properties, lookup) {
            @Override
            ExecutorService createExecutorService(int threadPoolSize, BlockingQueue<Runnable> queue) {
                assertEquals(MINIMUM_QUEUE_CAPACITY, queue.remainingCapacity());
                assertEquals(MINIMUM_THREAD_POOL_SIZE, threadPoolSize);

                return new ThreadPoolExecutor(
                        threadPoolSize,
                        threadPoolSize,
                        0L,
                        TimeUnit.MILLISECONDS,
                        queue
                );
            }
        };
    }

    BlockingQueue<Runnable> setupOffer() {
        given(properties.prefetchQueueCapacity()).willReturn(2);
        given(properties.prefetchThreadPoolSize()).willReturn(1);

        final AtomicReference<BlockingQueue<Runnable>> queueRef = new AtomicReference<>();
        processor = new PrefetchProcessor(properties, lookup) {
            @Override
            ExecutorService createExecutorService(int threadPoolSize, BlockingQueue<Runnable> queue) {
                ExecutorService execService = super.createExecutorService(threadPoolSize, queue);
                queueRef.set(queue);
                return execService;
            }
        };

        return queueRef.get();
    }

    @Test
    void offerSuccessful() throws InterruptedException {
        given(lookup.lookupFor(any(), any())).willReturn(Optional.of(logic));

        final var queue = setupOffer();
        processor.offer(accessor);

        assertEquals(1, queue.size());
        Runnable runnable = queue.take();
        runnable.run();

        verify(logic).preFetch(accessor);
    }

    @Test
    void offerNotPrefetchableLogic() {
        TransitionLogic logic = Mockito.mock(TransitionLogic.class);
        given(lookup.lookupFor(any(), any())).willReturn(Optional.of(logic));

        final var queue = setupOffer();
        processor.offer(accessor);

        assertEquals(0, queue.size());
        assertThat(
                logCaptor.warnLogs(),
                not(contains(Matchers.startsWith("Pre-fetch queue is FULL"))));
    }

    @Test
    void failedOfferQueueFull() {
        given(lookup.lookupFor(any(), any())).willReturn(Optional.of(logic));

        final var queue = setupOffer();
        processor.queue = new ArrayBlockingQueue<>(1);

        assertTrue(processor.offer(accessor));
        assertFalse(processor.offer(accessor));
        assertThat(
                logCaptor.warnLogs(),
                contains(Matchers.startsWith("Pre-fetch queue is FULL")));
    }

    @Test
    void offerEmptyTransitionLogic() {
        given(lookup.lookupFor(any(), any())).willReturn(Optional.empty());

        final var queue = setupOffer();
        processor.offer(accessor);

        assertEquals(0, queue.size());
    }

    @Test
    void offerExceptionThrownDuringRun() throws InterruptedException {
        given(lookup.lookupFor(any(), any())).willReturn(Optional.of(logic));
        doThrow(new RuntimeException("oh no")).when(logic).preFetch(accessor);

        final var queue = setupOffer();
        processor.offer(accessor);

        assertEquals(1, queue.size());
        Runnable runnable = queue.take();
        runnable.run();

        verify(logic).preFetch(accessor);
    }
}
