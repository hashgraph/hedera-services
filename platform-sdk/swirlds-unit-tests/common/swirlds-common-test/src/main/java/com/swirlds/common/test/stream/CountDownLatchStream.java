/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.stream.internal.LinkedObjectStream;
import java.util.concurrent.CountDownLatch;

/**
 * This stream contains a CountDownLatch(1) object.
 * Once this stream receives expected number of objects from its upper stream, the CountDownLatch.countDown will be
 * called.
 *
 * @param <T>
 * 		RunningHashable
 */
public class CountDownLatchStream<T extends RunningHashable> implements LinkedObjectStream<T> {
    /** the countDownLatch will be countDown once this stream receives expected count of objects */
    private CountDownLatch countDownLatch;
    /** how many objects it expects to receive from its upper stream */
    private int expectedCount;

    public CountDownLatchStream(final CountDownLatch countDownLatch, final int expectedCount) {
        this.countDownLatch = countDownLatch;
        this.expectedCount = expectedCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRunningHash(final Hash hash) {
        // intentionally blank
        // because it would be called by upperStream, but this stream don't use runningHash
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addObject(T t) {
        expectedCount--;
        if (expectedCount == 0) {
            countDownLatch.countDown();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        // intentionally blank
        // because it would be called by upperStream, but this stream don't need to clear anything
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        // intentionally blank
        // because it would be called by upperStream, but this stream don't have close logic
    }
}
