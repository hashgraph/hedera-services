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

package com.swirlds.common.test.threading;

import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;

import com.swirlds.common.threading.BlockingResourceProvider;
import com.swirlds.common.threading.locks.locked.LockedResource;
import com.swirlds.common.threading.utility.ThrowingRunnable;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class BlockingResourceProviderTest {
    /**
     * Tests the intended functionality of {@link BlockingResourceProvider}
     * <ul>
     *     <li>Starts up a number of providers and a single consumer</li>
     *     <li>The consumer waits for a resource and consumes it until the number of expected resources is met</li>
     *     <li>The providers fight over the permit and sometimes a resource and sometimes release the permit</li>
     * </ul>
     * We check the following:
     * <ul>
     *     <li>Before we provide a resource, we check if the previous one has already been consumed</li>
     *     <li>At the end, we check that each resource has been consumed exactly once</li>
     *     <li>That no exceptions have been thrown</li>
     *     <li>That all threads finished gracefully</li>
     * </ul>
     */
    @Test
    @Tag(TIME_CONSUMING)
    void testProvidersTakeTurns() throws ExecutionException, InterruptedException {
        final int numResources = 10;
        final int numThreads = 10;

        final ConcurrentTesting concurrentTesting = new ConcurrentTesting();
        final BlockingResourceProvider<Resource> provider = new BlockingResourceProvider<>();
        final AtomicReference<Resource> lastProvided = new AtomicReference<>();
        final ConcurrentMap<Resource, Resource> consumed = new ConcurrentHashMap<>();

        for (int i = 0; i < numThreads; i++) {
            concurrentTesting.addRunnable(new Provider(provider, lastProvided, consumed, i, numResources));
        }
        concurrentTesting.addRunnable(() -> {
            int numConsumed = 0;
            while (numConsumed < numThreads * numResources) {
                try (final LockedResource<Resource> lr = provider.waitForResource()) {
                    consumed.put(lr.getResource(), lr.getResource());
                    numConsumed++;
                }
            }
        });

        concurrentTesting.runForSeconds(5);

        Assertions.assertEquals(
                numThreads * numResources, consumed.size(), "each resource was supposed to be consumed exactly once");
    }

    private record Resource(int threadId, int sequence) {}

    private static final class Provider implements ThrowingRunnable {
        private final BlockingResourceProvider<Resource> provider;
        private final AtomicReference<Resource> lastProvided;
        private final ConcurrentMap<Resource, Resource> consumed;
        private final int threadId;
        private final int numResources;

        public Provider(
                final BlockingResourceProvider<Resource> provider,
                final AtomicReference<Resource> lastProvided,
                final ConcurrentMap<Resource, Resource> consumed,
                final int threadId,
                final int numResources) {
            this.provider = provider;
            this.lastProvided = lastProvided;
            this.consumed = consumed;
            this.threadId = threadId;
            this.numResources = numResources;
        }

        @Override
        public void run() throws InterruptedException {
            final Random r = new Random();
            int seq = 0;
            while (seq < numResources) {
                if (!provider.acquireProvidePermit()) {
                    continue;
                }
                if (r.nextBoolean()) {
                    provider.releaseProvidePermit();
                    continue;
                }
                final Resource resource = new Resource(threadId, seq);
                final Resource last = lastProvided.getAndSet(resource);
                if (last != null && !consumed.containsKey(last)) {
                    throw new RuntimeException(String.format(
                            "last (%s) resource provided has not been consumed. current: %s", last, resource));
                }
                provider.provide(resource);
                seq++;
            }
        }
    }
}
