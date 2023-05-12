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

import com.swirlds.common.io.IOIterator;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.common.utility.BinarySearch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * This iterator looks at a directory and iterates over the event stream files within it.
 */
public class EventStreamPathIterator implements Iterator<Path> {

    private final Iterator<Path> iterator;

    private static final String EVENT_FILE_EXTENSION = ".evts";

    public static final long FIRST_ROUND_AVAILABLE = 1;

    /**
     * Create an iterator that walks over event stream files within a directory.
     *
     * @param eventStreamDirectory
     * 		a directory containing event stream files
     * @param startingRound
     * 		return files guaranteed to contain all the events starting with a particular round.
     * 		May return some files that contain data prior to the indicated round. Will walk over
     * 		all event stream files if starting round is {@link #FIRST_ROUND_AVAILABLE}.
     * @throws IOException
     * 		if there is a problem reading files
     */
    public EventStreamPathIterator(final Path eventStreamDirectory, final long startingRound) throws IOException {

        final List<Path> eventStreamFiles = new ArrayList<>();
        try (final Stream<Path> walk = Files.walk(eventStreamDirectory)) {
            walk.filter(EventStreamPathIterator::isFileAnEventStreamFile)
                    .sorted(EventStreamPathIterator::compareEventStreamPaths)
                    .forEachOrdered(eventStreamFiles::add);
        }

        if (startingRound <= FIRST_ROUND_AVAILABLE) {
            // We are attempting to get events from the beginning of time.
            iterator = eventStreamFiles.iterator();
        } else {

            // This binary search is guaranteed to return a file that contains events
            // from the round before where we want to start. As long as there are no gaps
            // in the event stream files, this guarantees that we will observe all the events
            // from the target round.
            final int startingIndex =
                    (int) BinarySearch.throwingSearch(0, eventStreamFiles.size(), (final Long index) -> {
                        final Path eventStreamFile = eventStreamFiles.get(index.intValue());
                        return Long.compare(getFirstRoundInEventStreamFile(eventStreamFile), startingRound - 1);
                    });

            iterator = eventStreamFiles
                    .subList(startingIndex, eventStreamFiles.size())
                    .iterator();
        }
    }

    /**
     * Compare two event stream files based on creation date.
     */
    private static int compareEventStreamPaths(final Path pathA, final Path pathB) {
        // A nice property of dates is that they are naturally alphabetized by timestamp
        return pathA.getFileName().compareTo(pathB.getFileName());
    }

    /**
     * Check if a file is an event stream file.
     */
    private static boolean isFileAnEventStreamFile(final Path path) {
        return path.toString().endsWith(EVENT_FILE_EXTENSION);
    }

    /**
     * Look inside an event stream file and return the round of the first event.
     *
     * @param path
     * 		a path to an event stream file
     * @return the round of the first event in the file
     */
    private static long getFirstRoundInEventStreamFile(final Path path) throws IOException {
        try (final IOIterator<DetailedConsensusEvent> iterator = new EventStreamSingleFileIterator(path, true)) {
            if (!iterator.hasNext()) {
                throw new IllegalStateException("Event stream file contains no events");
            }
            return iterator.next().getConsensusData().getRoundReceived();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Path next() {
        return iterator.next();
    }
}
