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

package com.swirlds.platform.recovery.internal;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * <p>
 * Iterates over events in an event stream spanning multiple event stream files.
 * </p>
 *
 * <p>
 * This iterator validates the hashes of the events as it goes. It there is a gap in event stream
 * files, or if an event stream file contains corrupted data, this object will throw an IO exception
 * when that data is reached.
 * </p>
 *
 * <p>
 * The exception to this rule is the final event stream file. The final file is permitted to abruptly terminate
 * very end (if, for example, a node crashed while writing it). All complete events at the beginning of the
 * file are returned by this iterator.
 * </p>
 */
public class EventStreamMultiFileIterator implements IOIterator<DetailedConsensusEvent> {

    private final Iterator<Path> fileIterator;
    private long fileCount;
    private long byteCount;
    private Hash startHash;
    private final List<DetailedConsensusEvent> skippedEvents;
    private EventStreamSingleFileIterator eventIterator;
    private long damagedFileCount = 0;

    /**
     * Create an iterator that walks over events in an event stream spanning multiple files.
     *
     * @param fileIterator
     * 		an iterator that returns ordered event stream files
     * @param startingRound
     * 		do not return events prior to this round, or {@link EventStreamPathIterator#FIRST_ROUND_AVAILABLE}
     * 		if all events should be walked by this iterator
     * @throws IOException
     * 		if there is a problem reading the event stream
     * @throws NoSuchElementException
     * 		if the starting round can't be found
     */
    public EventStreamMultiFileIterator(final Iterator<Path> fileIterator, final long startingRound)
            throws IOException {

        this.fileIterator = fileIterator;
        this.startHash = null;
        this.skippedEvents = new ArrayList<>();

        // Remove events from before the requested round
        while (hasNext() && peek().getConsensusData().getRoundReceived() < startingRound) {
            skippedEvents.add(next());
        }
    }

    /**
     * Create an iterator that walks over all events in a given directory starting with a given round.
     *
     * @param eventStreamDirectory
     * 		the directory in question
     * @param startingRound
     * 		the round to start with, or {@link EventStreamPathIterator#FIRST_ROUND_AVAILABLE}
     * 		if all events should be walked by this iterator
     * @throws IOException
     * 		if there is a problem reading the event stream
     * @throws NoSuchElementException
     * 		if the starting round can't be found
     */
    public EventStreamMultiFileIterator(final Path eventStreamDirectory, final long startingRound) throws IOException {
        this(new EventStreamPathIterator(eventStreamDirectory, startingRound), startingRound);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (eventIterator != null) {
            eventIterator.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() throws IOException {
        if (eventIterator == null || !eventIterator.hasNext()) {
            if (!fileIterator.hasNext()) {
                // No more files to open
                return false;
            }

            final Hash previousHash = eventIterator == null ? null : eventIterator.getEndHash();

            final Path nextFile = fileIterator.next();
            fileCount++;
            final boolean toleratePartialFile = !fileIterator.hasNext();
            if (eventIterator != null) {
                // if we are done with the previous file, close it
                byteCount += eventIterator.getBytesRead();
                eventIterator.close();
                if (eventIterator.isDamaged()) {
                    damagedFileCount++;
                }
            }
            eventIterator = new EventStreamSingleFileIterator(nextFile, toleratePartialFile);
            if (startHash == null) {
                // when opening the first file, save the starting hash
                startHash = eventIterator.getStartHash();
            }

            if (previousHash != null && !eventIterator.getStartHash().equals(previousHash)) {
                throw new IOException("missing event stream file detected");
            }
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DetailedConsensusEvent peek() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return eventIterator.peek();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DetailedConsensusEvent next() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return eventIterator.next();
    }

    /**
     * @return the running hash at the start of the first file
     */
    public Hash getStartHash() {
        return startHash;
    }

    /**
     * @return events that have been read from a file but are not returned by {@link #next()} because they have an
     * 		earlier round then the one requested
     */
    public List<DetailedConsensusEvent> getSkippedEvents() {
        return skippedEvents;
    }

    /**
     * Get the number of files that have been opened so far.
     *
     * @return the number of files opened so far
     */
    public long getFileCount() {
        return fileCount;
    }

    /**
     * Get the number of bytes read from all files so far.
     */
    public long getBytesRead() {
        return byteCount + eventIterator.getBytesRead();
    }

    /**
     * Get the number of files that are damaged.
     */
    public long getDamagedFileCount() {
        long damageCount = damagedFileCount;
        if (eventIterator != null && eventIterator.isDamaged()) {
            damageCount++;
        }

        return damageCount;
    }
}
