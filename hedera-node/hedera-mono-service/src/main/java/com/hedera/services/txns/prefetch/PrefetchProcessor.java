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

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.txns.PreFetchableTransition;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Processing dispatch layer for transactions submitted during the prepare stage (aka expand
 * signatures) that uses an {@code ExecutorService} to schedule the tasks to a static thread pool.
 * The tasks are responsible for fetching data that can be used during the serial execution portion
 * of the transaction (for example, loading of EVM contract bytecode).
 */
@Singleton
public class PrefetchProcessor {
    private static final Logger logger = LogManager.getLogger(PrefetchProcessor.class);

    @VisibleForTesting static final int MINIMUM_QUEUE_CAPACITY = 10_000;

    @VisibleForTesting static final int MINIMUM_THREAD_POOL_SIZE = 2;

    BlockingQueue<Runnable> queue;
    ExecutorService executorService;
    TransitionLogicLookup lookup;

    @Inject
    public PrefetchProcessor(NodeLocalProperties properties, TransitionLogicLookup lookup) {
        final int queueSize = Math.max(properties.prefetchQueueCapacity(), MINIMUM_QUEUE_CAPACITY);
        final int threadPoolSize =
                Math.max(properties.prefetchThreadPoolSize(), MINIMUM_THREAD_POOL_SIZE);

        this.lookup = lookup;
        queue = new ArrayBlockingQueue<>(queueSize);
        executorService = createExecutorService(threadPoolSize, queue);
    }

    @VisibleForTesting
    ExecutorService createExecutorService(int threadPoolSize, BlockingQueue<Runnable> queue) {
        final var executor =
                new ThreadPoolExecutor(
                        threadPoolSize, threadPoolSize, 0L, TimeUnit.MILLISECONDS, queue);
        executor.setRejectedExecutionHandler(
                (runnable, execService) -> logger.warn("Pre-fetch queue is FULL!"));
        executor.prestartAllCoreThreads();
        return executor;
    }

    /**
     * Attempts to schedule a pre-fetch task for the given transaction. A task will be created only
     * if the transition logic associated with the transaction request type implements {@code
     * PreFetchableTransition}. If the task cannot be scheduled due to the schedule queue being
     * full, the task will be skipped. The pre-fetch action is optional and is only intended for
     * performance optimization; the handleTransaction portion of {@code EventFlow} will pay the
     * cost of whatever the pre-fetch operation was.
     *
     * @param accessor the transaction accessor
     */
    public void submit(SwirldsTxnAccessor accessor) {
        final var opt = lookup.lookupFor(accessor.getFunction(), accessor.getTxn());

        if (opt.isPresent()) {
            final var logic = opt.get();
            if (logic instanceof PreFetchableTransition transition) {
                executorService.execute(
                        () -> {
                            try {
                                transition.preFetch(accessor);
                            } catch (RuntimeException e) {
                                logger.warn("Exception thrown during pre-fetch", e);
                            }
                        });
            }
        }
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}
