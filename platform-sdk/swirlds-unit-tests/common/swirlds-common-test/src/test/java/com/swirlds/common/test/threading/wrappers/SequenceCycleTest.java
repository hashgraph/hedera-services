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

package com.swirlds.common.test.threading.wrappers;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.utility.SequenceCycle;
import com.swirlds.common.threading.utility.ThrowingRunnable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SequenceCycleTest {
    private static final int WAIT_TIME = 5;
    private static final TimeUnit WAIT_TIME_UNIT = TimeUnit.SECONDS;

    /**
     * Tests the SequenceCycle by forcing a cycle to block until the test unblocks it
     */
    @Test
    void test() throws InterruptedException, ExecutionException, TimeoutException {
        final CountDownLatch consumerWait = new CountDownLatch(1);

        final CountDownLatch waiting = new CountDownLatch(1);
        final InterruptableConsumer<Void> consumer = v -> {
            waiting.countDown();
            consumerWait.await();
        };

        final SequenceCycle<Void> cycle = new SequenceCycle<>(consumer, ms -> {});

        final ExecutorService executor = Executors.newFixedThreadPool(2);

        final Future<Void> consumerFuture = executor.submit((ThrowingRunnable) () -> cycle.accept(null));

        // wait until the consumer starts blocking
        Assertions.assertTrue(waiting.await(WAIT_TIME, WAIT_TIME_UNIT), "the consumer did not start the cycle");

        // once we are sure the consumer started the cycle, we start waiting for it to end
        final Future<Void> waitFuture = executor.submit((ThrowingRunnable) cycle::waitForCurrentSequenceEnd);

        Assertions.assertFalse(consumerFuture.isDone(), "the consumer should be blocked");
        Assertions.assertFalse(waitFuture.isDone(), "we should be waiting because the cycle");

        // unblock the consumer
        consumerWait.countDown();

        consumerFuture.get(WAIT_TIME, WAIT_TIME_UNIT);
        waitFuture.get(WAIT_TIME, WAIT_TIME_UNIT);

        // no cycle is in progress, so nothing should be blocking the wait
        final Future<Void> noSeqWait = executor.submit((ThrowingRunnable) cycle::waitForCurrentSequenceEnd);
        noSeqWait.get(WAIT_TIME, WAIT_TIME_UNIT);
    }
}
