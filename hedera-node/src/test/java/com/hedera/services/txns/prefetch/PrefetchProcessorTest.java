/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.prefetch;

import static com.hedera.services.txns.prefetch.PrefetchProcessor.MINIMUM_QUEUE_CAPACITY;
import static com.hedera.services.txns.prefetch.PrefetchProcessor.MINIMUM_THREAD_POOL_SIZE;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.txns.PreFetchableTransition;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class PrefetchProcessorTest {
    @Mock NodeLocalProperties properties;
    @Mock TransitionLogicLookup lookup;
    @Mock PlatformTxnAccessor accessor;
    @Mock PreFetchableTransition logic;

    PrefetchProcessor processor;
    List<Runnable> executed = new ArrayList<>();
    List<Runnable> rejected = new ArrayList<>();

    @AfterEach
    void teardown() {
        processor.shutdown();
    }

    @Test
    void createSuccessful() {
        given(properties.prefetchQueueCapacity()).willReturn(MINIMUM_QUEUE_CAPACITY + 1);
        given(properties.prefetchThreadPoolSize()).willReturn(MINIMUM_THREAD_POOL_SIZE + 1);

        processor =
                new PrefetchProcessor(properties, lookup) {
                    @Override
                    ExecutorService createExecutorService(
                            int threadPoolSize, BlockingQueue<Runnable> queue) {
                        assertEquals(MINIMUM_QUEUE_CAPACITY + 1, queue.remainingCapacity());
                        assertEquals(MINIMUM_THREAD_POOL_SIZE + 1, threadPoolSize);

                        return new ThreadPoolExecutor(
                                threadPoolSize, threadPoolSize, 0L, TimeUnit.MILLISECONDS, queue);
                    }
                };
    }

    @Test
    void createWithInvalidParameters() {
        given(properties.prefetchQueueCapacity()).willReturn(2);
        given(properties.prefetchThreadPoolSize()).willReturn(1);

        processor =
                new PrefetchProcessor(properties, lookup) {
                    @Override
                    ExecutorService createExecutorService(
                            int threadPoolSize, BlockingQueue<Runnable> queue) {
                        assertEquals(MINIMUM_QUEUE_CAPACITY, queue.remainingCapacity());
                        assertEquals(MINIMUM_THREAD_POOL_SIZE, threadPoolSize);

                        return new ThreadPoolExecutor(
                                threadPoolSize, threadPoolSize, 0L, TimeUnit.MILLISECONDS, queue);
                    }
                };
    }

    BlockingQueue<Runnable> setupSubmit() {
        given(properties.prefetchQueueCapacity()).willReturn(2);
        given(properties.prefetchThreadPoolSize()).willReturn(1);

        final AtomicReference<BlockingQueue<Runnable>> queueRef = new AtomicReference<>();
        processor =
                new PrefetchProcessor(properties, lookup) {
                    @Override
                    ExecutorService createExecutorService(
                            int threadPoolSize, BlockingQueue<Runnable> queue) {
                        queue = new ArrayBlockingQueue<>(2);
                        ThreadPoolExecutor execService =
                                new ThreadPoolExecutor(
                                        threadPoolSize,
                                        threadPoolSize,
                                        0L,
                                        TimeUnit.MILLISECONDS,
                                        queue) {
                                    @Override
                                    @java.lang.SuppressWarnings("java:S2925")
                                    protected void beforeExecute(Thread t, Runnable r) {
                                        try {
                                            executed.add(r);
                                            Thread.sleep(
                                                    10); // need to wait to allow assertions to work
                                        } catch (InterruptedException e) {
                                            // noop
                                        }
                                    }
                                };
                        execService.setRejectedExecutionHandler(
                                (runnable, exec) -> {
                                    rejected.add(runnable);
                                });

                        this.queue = queue;
                        queueRef.set(queue);
                        return execService;
                    }
                };

        return queueRef.get();
    }

    @Test
    void submitSuccessful() {
        given(lookup.lookupFor(any(), any())).willReturn(Optional.of(logic));

        final var queue = setupSubmit();
        processor.submit(accessor);

        await().until(() -> executed.size() == 1);

        verify(logic).preFetch(accessor);
    }

    @Test
    void submitNotPrefetchableLogic() {
        TransitionLogic logic = Mockito.mock(TransitionLogic.class);
        given(lookup.lookupFor(any(), any())).willReturn(Optional.of(logic));

        final var queue = setupSubmit();
        processor.submit(accessor);

        await().atMost(200, TimeUnit.MILLISECONDS)
                .until(() -> executed.size() == 0 && rejected.size() == 0);
    }

    @Test
    void failedSubmitQueueFull() {
        given(lookup.lookupFor(any(), any())).willReturn(Optional.of(logic));

        final var queue = setupSubmit();
        for (int i = 0; i < 20; i++) {
            processor.submit(accessor);
        }

        await().atMost(10, TimeUnit.SECONDS).until(() -> rejected.size() > 0);
    }

    @Test
    void submitEmptyTransitionLogic() {
        given(lookup.lookupFor(any(), any())).willReturn(Optional.empty());

        setupSubmit();
        assertDoesNotThrow(() -> processor.submit(accessor));
    }

    @Test
    void submitExceptionThrownDuringRun() {
        given(lookup.lookupFor(any(), any())).willReturn(Optional.of(logic));
        doThrow(new RuntimeException("oh no")).when(logic).preFetch(accessor);

        final var queue = setupSubmit();
        processor.submit(accessor);

        await().until(() -> executed.size() == 1);

        verify(logic).preFetch(accessor);
    }
}
