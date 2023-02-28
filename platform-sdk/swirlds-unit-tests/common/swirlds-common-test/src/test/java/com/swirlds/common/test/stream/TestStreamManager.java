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

package com.swirlds.common.test.stream;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.stream.HashCalculatorForStream;
import com.swirlds.common.stream.MultiStream;
import com.swirlds.common.stream.QueueThreadObjectStream;
import com.swirlds.common.stream.QueueThreadObjectStreamConfiguration;
import com.swirlds.common.stream.RunningHashCalculatorForStream;
import com.swirlds.common.system.NodeId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * For testing streaming performance;
 * takes objects from {@link ObjectForTestStreamGenerator},
 * sends objects to LinkedObjectStream objects for calculating Hash and RunningHash
 */
public class TestStreamManager {
    private static final Logger logger = LogManager.getLogger(TestStreamManager.class);
    private static final NodeId NODE_ID = new NodeId(false, 0);
    /**
     * receives {@link ObjectForTestStream}s then passes to hashQueueThread
     */
    private final MultiStream<ObjectForTestStream> multiStream;
    /** receives {@link ObjectForTestStream}s from multiStream, then passes to hashCalculator */
    private final QueueThreadObjectStream<ObjectForTestStream> hashQueueThread;
    /**
     * receives {@link ObjectForTestStream}s from hashQueueThread, calculates this object's Hash, then passes to
     * runningHashCalculator
     */
    private final HashCalculatorForStream<ObjectForTestStream> hashCalculator;
    /** initial running Hash of records */
    private Hash initialHash = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

    public TestStreamManager(final CountDownLatch countDownLatch, final int expectedCount) {
        CountDownLatchStream<ObjectForTestStream> countDownLatchStream =
                new CountDownLatchStream<>(countDownLatch, expectedCount);
        // receives {@link ObjectForTestStream}s from hashCalculator, calculates and set runningHash for this object
        final RunningHashCalculatorForStream<ObjectForTestStream> runningHashCalculator =
                new RunningHashCalculatorForStream<>(countDownLatchStream);
        hashCalculator = new HashCalculatorForStream<>(runningHashCalculator);
        hashQueueThread = new QueueThreadObjectStreamConfiguration<ObjectForTestStream>(getStaticThreadManager())
                .setForwardTo(hashCalculator)
                .build();
        hashQueueThread.start();

        multiStream = new MultiStream<>(List.of(hashQueueThread));
        multiStream.setRunningHash(initialHash);
    }

    public TestStreamManager() {
        // receives {@link ObjectForTestStream}s from hashCalculator, calculates and set runningHash for this object
        final RunningHashCalculatorForStream<ObjectForTestStream> runningHashCalculator =
                new RunningHashCalculatorForStream<>();
        hashCalculator = new HashCalculatorForStream<>(runningHashCalculator);
        hashQueueThread = new QueueThreadObjectStreamConfiguration<ObjectForTestStream>(getStaticThreadManager())
                .setForwardTo(hashCalculator)
                .build();

        multiStream = new MultiStream<>(List.of(hashQueueThread));
        multiStream.setRunningHash(initialHash);
    }

    /**
     * receives a object each time,
     * sends it to multiStream which then sends to two queueThread for calculating runningHash and writing to file
     *
     * @param recordStreamObject
     * 		the {@link ObjectForTestStream} object to be added
     */
    public void addObjectForTestStream(final ObjectForTestStream recordStreamObject) {
        multiStream.addObject(recordStreamObject);
    }

    /**
     * for unit testing
     *
     * @return a copy of initialHash
     */
    public Hash getInitialHash() {
        return new Hash(initialHash);
    }

    /**
     * sets initialHash after loading from signed state
     *
     * @param initialHash
     * 		current runningHash of all {@link ObjectForTestStream}s
     */
    public void setInitialHash(final Hash initialHash) {
        this.initialHash = initialHash;
        logger.info("RecordStreamManager::setInitialHash: {}", () -> initialHash);
        multiStream.setRunningHash(initialHash);
    }
}
