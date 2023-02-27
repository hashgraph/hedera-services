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

package com.swirlds.platform.event.preconsensus;

import com.swirlds.common.io.IOIterator;
import com.swirlds.platform.internal.EventImpl;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterates over events from a sequence of preconsensus event files.
 */
public class PreConsensusEventMultiFileIterator implements IOIterator<EventImpl> {

    private final Iterator<PreConsensusEventFile> fileIterator;
    private PreConsensusEventFileIterator currentIterator;
    private final long minimumGeneration;
    private EventImpl next;
    private int truncatedFileCount = 0;

    /**
     * Create an iterator that walks over events in a series of event files.
     *
     * @param minimumGeneration
     * 		the minimum generation of events to return, events with lower
     * 		generations are not returned
     * @param fileIterator
     * 		an iterator that walks over event files
     */
    public PreConsensusEventMultiFileIterator(
            final long minimumGeneration, final Iterator<PreConsensusEventFile> fileIterator) {

        this.fileIterator = fileIterator;
        this.minimumGeneration = minimumGeneration;
    }

    /**
     * Find the next event that should be returned.
     */
    private void findNext() throws IOException {
        while (next == null) {
            if (currentIterator == null || !currentIterator.hasNext()) {
                if (currentIterator != null && currentIterator.hasPartialEvent()) {
                    truncatedFileCount++;
                }

                if (!fileIterator.hasNext()) {
                    break;
                }

                currentIterator = new PreConsensusEventFileIterator(fileIterator.next(), minimumGeneration);
            } else {
                next = currentIterator.next();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() throws IOException {
        findNext();
        return next != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventImpl next() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        try {
            return next;
        } finally {
            next = null;
        }
    }

    /**
     * Get the number of files that had partial event data at the end. This can happen if JVM is shut down
     * abruptly while and event is being written to disk.
     *
     * @return the number of files that had partial event data at the end that have been encountered so far
     */
    public int getTruncatedFileCount() {
        return truncatedFileCount;
    }
}
