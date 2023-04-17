/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
import com.swirlds.virtualmap.internal.RecordAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.commons.lang3.tuple.Pair;

/**
 * A utility for migrating data within a virtual map from one format to another.
 */
public final class VirtualMapMigration {

    private VirtualMapMigration() {}

    private static final int QUEUE_CAPACITY = 10_000;

    private static final String COMPONENT_NAME = "virtual-map-migration";

    /**
     * Extract all key-value pairs from a virtual map and pass it to a handler.
     *
     * @param threadManager
     * 		responsible for creating and managing threads
     * @param source
     * 		a virtual map to read from, will not be modified by this method
     * @param threadCount
     * 		the number of threads used for reading from the original map
     * @param <K>
     * 		the type of the key
     * @param <V>
     * 		the type of the value
     */
    public static <K extends VirtualKey, V extends VirtualValue> void extractVirtualMapData(
            final ThreadManager threadManager,
            final VirtualMap<K, V> source,
            final InterruptableConsumer<Pair<K, V>> handler,
            final int threadCount)
            throws InterruptedException {

        final long firstLeafPath = source.getState().getFirstLeafPath();
        final long lastLeafPath = source.getState().getLastLeafPath();
        if (firstLeafPath == Path.INVALID_PATH || lastLeafPath == Path.INVALID_PATH) {
            return;
        }

        final RecordAccessor<K, V> recordAccessor = source.getRoot().getRecords();

        final long size = source.size();

        // A collection of threads iterate over the map. Each thread writes into its own output queue.
        final List<Thread> threads = new ArrayList<>(threadCount);
        final List<BlockingQueue<Pair<K, V>>> threadQueues = new ArrayList<>(threadCount);

        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {

            final BlockingQueue<Pair<K, V>> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
            threadQueues.add(queue);

            // Java only allows final values to be passed into a lambda
            final int index = threadIndex;

            threads.add(threadManager
                    .newThreadConfiguration()
                    .setComponent(COMPONENT_NAME)
                    .setThreadName("reader-" + threadCount)
                    .setInterruptableRunnable(() -> {
                        for (long path = firstLeafPath + index; path <= lastLeafPath; path += threadCount) {
                            final VirtualLeafRecord<K, V> leafRecord = recordAccessor.findLeafRecord(path, false);
                            queue.put(Pair.of(leafRecord.getKey(), leafRecord.getValue()));
                        }
                    })
                    .build(true));
        }

        // Buffers for reading from the thread output queues.
        final List<List<Pair<K, V>>> buffers = new ArrayList<>(threadCount);
        final List<Integer> bufferIndices = new ArrayList<>(threadCount);
        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
            buffers.add(new ArrayList<>(QUEUE_CAPACITY));
            bufferIndices.add(0);
        }

        // Aggregate values from queues and pass them to the handler.
        for (int index = 0; index < size; index++) {
            try {

                final int queueIndex = index % threadCount;

                final BlockingQueue<Pair<K, V>> queue = threadQueues.get(queueIndex);
                final List<Pair<K, V>> buffer = buffers.get(queueIndex);

                // Fill the buffer if needed
                int bufferIndex = bufferIndices.get(queueIndex);
                while (buffer.size() <= bufferIndex) {
                    buffer.clear();
                    queue.drainTo(buffer, QUEUE_CAPACITY);
                    bufferIndex = 0;
                }

                handler.accept(buffer.get(bufferIndex));

                bufferIndices.set(queueIndex, bufferIndex + 1);

            } catch (final InterruptedException e) {
                // If we are interrupted, stop all of the reader threads
                threads.forEach(Thread::interrupt);
                throw e;
            }
        }
    }
}
