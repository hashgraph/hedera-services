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

package com.swirlds.platform.recovery.events;

import com.swirlds.common.io.IOIterator;
import com.swirlds.common.stream.RunningHashCalculatorForStream;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.common.utility.CommonUtils;
import java.io.IOException;

/**
 * A wrapper around {@link EventStreamMultiFileIterator}
 * that computes a running hash of the iterated events.
 */
public class MultiFileRunningHashIterator implements IOIterator<DetailedConsensusEvent> {
    private final EventStreamMultiFileIterator iterator;
    private final RunningHashCalculatorForStream<DetailedConsensusEvent> runningHashCalculator;

    /**
     * @param iterator
     * 		the iterator that reads event stream files
     */
    public MultiFileRunningHashIterator(final EventStreamMultiFileIterator iterator) {
        CommonUtils.throwArgNull(iterator, "iterator");
        this.iterator = iterator;
        this.runningHashCalculator = new RunningHashCalculatorForStream<>();

        runningHashCalculator.setRunningHash(iterator.getStartHash());
        for (final DetailedConsensusEvent skippedEvent : iterator.getSkippedEvents()) {
            runningHashCalculator.addObject(skippedEvent);
        }
    }

    @Override
    public boolean hasNext() throws IOException {
        return iterator.hasNext();
    }

    @Override
    public DetailedConsensusEvent next() throws IOException {
        final DetailedConsensusEvent next = iterator.next();
        runningHashCalculator.addObject(next);
        return next;
    }

    @Override
    public DetailedConsensusEvent peek() throws IOException {
        return iterator.peek();
    }

    @Override
    public void close() {
        iterator.close();
    }

    /**
     * Get the number of bytes read from the disk.
     */
    public long getBytesRead() {
        return iterator.getBytesRead();
    }

    /**
     * Get the number of files that are damaged.
     */
    public long getDamagedFileCount() {
        return iterator.getDamagedFileCount();
    }
}
