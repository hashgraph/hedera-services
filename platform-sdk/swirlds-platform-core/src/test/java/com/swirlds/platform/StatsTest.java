/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.swirlds.common.statistics.StatsRunningAverage;
import com.swirlds.test.framework.TestQualifierTags;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class StatsTest {
    /**
     * default half-life for statistics
     */
    private static final double DEFAULT_HALF_LIFE = 10;
    /**
     * avg time taken to execute the FCQueue getHash method, including locks (in microseconds)
     */
    @SuppressWarnings("removal")
    private static final StatsRunningAverage stats = new StatsRunningAverage(DEFAULT_HALF_LIFE);

    /**
     * how many threads we use for testing
     */
    private static final int THREADS_NUM = 100;

    /**
     * how many time we call recordValue()
     */
    private static final int RECORD_TIME = 10_000_000;

    /**
     * the maximum time to wait
     */
    private static final int TIME_OUT = 2;

    /**
     * Starts multiple threads call recordValue().
     * This test should not throw ArrayIndexOutOfBoundsException.
     */
    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void recordValueMultiThreadsTest() {
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(THREADS_NUM);
            for (int i = 0; i < RECORD_TIME; i++) {
                executorService.submit(
                        () -> stats.recordValue(ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE)));
            }
            executorService.shutdown();
            executorService.awaitTermination(TIME_OUT, TimeUnit.SECONDS);
        } catch (Exception exception) {
            assertFalse(
                    exception instanceof ArrayIndexOutOfBoundsException,
                    "This test should not throw ArrayIndexOutOfBoundsException");
        }
    }
}
