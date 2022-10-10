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
package com.hedera.services.stream;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

import com.hedera.services.context.properties.NodeLocalProperties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NonBlockingHandoff {
    private static final int MIN_CAPACITY = 5_000;

    private ExecutorService executor = newSingleThreadExecutor();

    private final AtomicBoolean timeToStop = new AtomicBoolean(false);
    private final RecordStreamManager recordStreamManager;
    private final BlockingQueue<RecordStreamObject> queue;

    @Inject
    public NonBlockingHandoff(
            final RecordStreamManager recordStreamManager,
            final NodeLocalProperties nodeLocalProperties) {
        this.recordStreamManager = recordStreamManager;
        final int capacity =
                Math.max(MIN_CAPACITY, nodeLocalProperties.recordStreamQueueCapacity());
        queue = new ArrayBlockingQueue<>(capacity);
        executor.execute(this::handoff);
        Runtime.getRuntime().addShutdownHook(new Thread(getShutdownHook()));
    }

    public boolean offer(RecordStreamObject rso) {
        return queue.offer(rso);
    }

    private void handoff() {
        while (!timeToStop.get()) {
            try {
                recordStreamManager.addRecordStreamObject(queue.take());
            } catch (final InterruptedException e) {
                // Thread interrupted because of shutdown.
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    ExecutorService getExecutor() {
        return executor;
    }

    void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    Runnable getShutdownHook() {
        return () -> {
            timeToStop.set(true);
            executor.shutdown();
        };
    }

    AtomicBoolean getTimeToStop() {
        return timeToStop;
    }
}
