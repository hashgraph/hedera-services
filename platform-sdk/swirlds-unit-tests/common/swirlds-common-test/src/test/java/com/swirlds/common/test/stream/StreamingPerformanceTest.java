/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.test.framework.TestQualifierTags;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StreamingPerformanceTest {

    private static final int TARGET_TPS = 400_000;
    private static final int DURATION_SECS = 1;
    private static final int OBJECTS_COUNT = TARGET_TPS * DURATION_SECS;

    private static List<ObjectForTestStream> objects;

    void generateObjects(final int payLoadSize) {
        // generating objects
        objects = new LinkedList<>();
        for (int i = 0; i < OBJECTS_COUNT; i++) {
            objects.add(ObjectForTestStream.getRandomObjectForTestStream(payLoadSize));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 150, 200})
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void performanceTest(final int payLoadSize) throws InterruptedException {
        generateObjects(payLoadSize);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final TestStreamManager streamManager = new TestStreamManager(countDownLatch, OBJECTS_COUNT);
        final Iterator<ObjectForTestStream> iterator = objects.iterator();

        final StopWatch stopWatchTotal = new StopWatch();
        stopWatchTotal.start();
        final StopWatch stopWatchAddingObjects = new StopWatch();
        stopWatchAddingObjects.start();
        while (iterator.hasNext()) {
            streamManager.addObjectForTestStream(iterator.next());
        }

        stopWatchAddingObjects.stop();
        long actualTimeInThread = stopWatchAddingObjects.getTime(TimeUnit.MILLISECONDS);
        System.out.println(actualTimeInThread + " millis adding objects");

        countDownLatch.await();

        stopWatchTotal.stop();
        final long actualTimeInTotal = stopWatchTotal.getTime(TimeUnit.MILLISECONDS);
        System.out.println(actualTimeInTotal + " millis in total");
        assertTrue(actualTimeInTotal < 2_800, String.format("actualTime %d < 2800 ms.", actualTimeInTotal));
    }
}
