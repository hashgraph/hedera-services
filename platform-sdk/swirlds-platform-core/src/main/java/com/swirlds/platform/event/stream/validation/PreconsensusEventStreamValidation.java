/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.stream.validation;

import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.event.preconsensus.PreconsensusEventFileManager.NO_MINIMUM_GENERATION;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.IOIterator;
import com.swirlds.platform.event.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFile;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFileIterator;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFileReader;
import com.swirlds.platform.event.validation.InvalidStreamException;
import com.swirlds.platform.state.signed.SavedStateMetadata;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for validating the preconsensus event stream.
 */
public final class PreconsensusEventStreamValidation {

    private static final Logger logger = LogManager.getLogger(PreconsensusEventStreamValidation.class);

    private PreconsensusEventStreamValidation() {}

    /**
     * Validate the preconsensus event stream. Returns a set containing descriptors for all events that were found in
     * the stream. The following checks are performed:
     *
     * <ul>
     * <li>that there exists at least one PCES file</li>
     * <li>that the generations stored by the PCES "cover" all states on disk</li>
     * <li>that the number of discontinuities in the stream do not exceed a specified maximum</li>
     * <li>files do not contain events with illegal generations</li>
     * <li>events are in topological order</li>
     * <li>events only show up once in a stream (violations are permitted when there are discontinuities)</li>
     * <li>checks performed by {@link PreconsensusEventFileReader#fileSanityChecks(
     *boolean, long, long, long, long, Instant, PreconsensusEventFile) fileSanityChecks()}</li>
     * </ul>
     *
     * @param cryptography             the cryptography object to use for hashing
     * @param states                   metadata for all saved states in the state directory. Most recent state is first
     *                                 in the last, oldest state is last.
     * @param streamDirectory          the root of the directory tree where PCES files can be found.
     * @param permittedDiscontinuities the permitted number of discontinuities in the stream
     * @return @return a set containing descriptors for all events in the stream.
     */
    @NonNull
    public static Set<EventDescriptor> validatePreconsensusEventStream(
            @NonNull final Cryptography cryptography,
            @NonNull final List<SavedStateMetadata> states,
            @NonNull final Path streamDirectory,
            @NonNull final int permittedDiscontinuities)
            throws IOException {

        // All events in the stream are inserted here
        final Set<EventDescriptor> allDescriptors = new HashSet<>();

        // This is similar to the allDescriptors set except that it is cleared every time a discontinuity is encountered
        final Set<EventDescriptor> descriptorsInContiguousStream = new HashSet<>();

        // This method already does validation on top level PCES data, no need to repeat that logic.
        final List<PreconsensusEventFile> files = PreconsensusEventFileReader.readFilesFromDisk(streamDirectory, false);

        if (files.isEmpty()) {
            throw new InvalidStreamException("No preconsensus event files found.");
        }

        logger.info(
                STARTUP.getMarker(),
                """
                        Found {} preconsensus event files.
                            First PCES file: {}
                            Last PCES file:  {}""",
                files.size(),
                files.get(0),
                files.get(files.size() - 1));

        final Set<Hash> parents = new HashSet<>();
        long previousOrigin = 0;

        int discontinuityCount = 0;
        for (final PreconsensusEventFile file : files) {
            if (file.getOrigin() != previousOrigin) {
                // We've crossed a discontinuity in the stream. Clear the parent set since it's possible that
                // events in stream files with different origins may violate topological constraints.
                previousOrigin = file.getOrigin();
                parents.clear();
                descriptorsInContiguousStream.clear();
                discontinuityCount++;
            }

            validatePreconsensusEventFile(cryptography, file, allDescriptors, descriptorsInContiguousStream, parents);
        }

        if (discontinuityCount > permittedDiscontinuities) {
            throw new InvalidStreamException("Preconsensus event stream has " + discontinuityCount
                    + " discontinuities, but only " + permittedDiscontinuities + " are permitted.");
        }

        // Oldest state file will be last in the list. We should have PCES files covering this state.
        final SavedStateMetadata oldestState = states.get(states.size() - 1);
        final PreconsensusEventFile firstFile = files.get(0);

        if (oldestState.minimumGenerationNonAncient() < firstFile.getMinimumGeneration()) {
            throw new InvalidStreamException("Oldest state file has minimum generation "
                    + oldestState.minimumGenerationNonAncient() + " but the first PCES file has minimum generation "
                    + firstFile.getMinimumGeneration()
                    + ". This means that PCES files were prematurely deleted.");
        }

        logger.info(
                STARTUP.getMarker(),
                "Preconsensus event stream is valid.  Files contained {} events. Discontinuity count: {}.",
                allDescriptors.size(),
                discontinuityCount);

        return allDescriptors;
    }

    /**
     * Validate the contents of a PCES file and add descriptors of its events to the given set.
     *
     * @param file                             the file to validate
     * @param allDescriptors                   a set where all events in the stream are put
     * @param allDescriptorsInContiguousStream all events are put into this set, but the set is cleared when crossing a
     *                                         discontinuity
     * @param parents                          a set of the hashes of all events that have been parent to at least one
     *                                         event in the stream. Once a hash is added to the parent set, we should
     *                                         never encounter an event with that hash. If we do, that means we received
     *                                         a parent event after one of its children.
     */
    private static void validatePreconsensusEventFile(
            @NonNull final Cryptography cryptography,
            @NonNull final PreconsensusEventFile file,
            @NonNull final Set<EventDescriptor> allDescriptors,
            @NonNull final Set<EventDescriptor> allDescriptorsInContiguousStream,
            @NonNull final Set<Hash> parents)
            throws IOException {

        final IOIterator<GossipEvent> iterator = new PreconsensusEventFileIterator(file, NO_MINIMUM_GENERATION);
        while (iterator.hasNext()) {
            final GossipEvent event = iterator.next();
            cryptography.digestSync(event.getHashedData());

            // Make sure that each event is allowed to be in this file
            final long generation = event.getGeneration();
            if (event.getGeneration() < file.getMinimumGeneration()) {
                throw new InvalidStreamException(
                        "Event " + event.getHashedData().getHash() + " has generation "
                                + generation + " which is less than the minimum generation for the file "
                                + file);
            }
            if (event.getGeneration() > file.getMaximumGeneration()) {
                throw new InvalidStreamException(
                        "Event " + event.getHashedData().getHash() + " has generation "
                                + generation + " which is greater than the maximum generation for the file "
                                + file);
            }

            // Verify that events are in topological order
            if (parents.contains(event.getHashedData().getHash())) {
                throw new InvalidStreamException(
                        "Event " + event.getHashedData().getHash() + " is a parent of a previous event in the stream, "
                                + "preconsensus event stream is not in topological order");
            }
            parents.add(event.getHashedData().getSelfParentHash());
            parents.add(event.getHashedData().getOtherParentHash());


            event.buildDescriptor();
            allDescriptors.add(event.getDescriptor());
            final boolean alreadyPresent = allDescriptorsInContiguousStream.add(event.getDescriptor());

            if (alreadyPresent) {
                throw new InvalidStreamException(
                        "Event " + event.getHashedData().getHash() + " is already present in the stream.");
            }
        }
    }
}
