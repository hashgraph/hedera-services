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

package com.swirlds.platform.event.validation;

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
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SavedStateMetadata;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for validating CES and PCES files.
 */
public final class EventStreamValidation {

    private static final Logger logger = LogManager.getLogger(EventStreamValidation.class);

    private EventStreamValidation() {}

    /**
     * Validate the CES and PCES files in the given directories.
     *
     * @param stateDirectory                   the root of the directory tree where state files are saved.
     * @param consensusEventStreamDirectory    the directory where CES files can be found.
     * @param preconsensusEventStreamDirectory the root of the directory tree where PCES files can be found.
     * @param permittedFileBreaks              the permitted number of breaks in the file (usually just the number of
     *                                         reconnects).
     * @throws InvalidStreamException if invalid streams are detected
     */
    public static void validateStreams(
            @NonNull final Cryptography cryptography,
            @NonNull final Path stateDirectory,
            @NonNull final Path consensusEventStreamDirectory,
            @NonNull final Path preconsensusEventStreamDirectory,
            final int permittedFileBreaks)
            throws IOException {

        Objects.requireNonNull(stateDirectory);
        Objects.requireNonNull(consensusEventStreamDirectory);
        Objects.requireNonNull(preconsensusEventStreamDirectory);

        final List<SavedStateMetadata> states = loadStateMetadata(stateDirectory);

        final Set<EventDescriptor> preconsensusEvents =
                validatePreconsensusEventStream(cryptography, states, preconsensusEventStreamDirectory);

        final Set<EventDescriptor> consensusEvents =
                validateConsensusEventStream(states, consensusEventStreamDirectory, permittedFileBreaks);

        validatePreconsensusAgainstConsensusStreams(preconsensusEvents, consensusEvents);
    }

    /**
     * Load the metadata for all saved states in the state directory.
     *
     * @param stateDirectory the root of the directory tree where state files are saved.
     * @return a list of metadata for all saved states in the state directory ordered from most recent to least recent.
     */
    @NonNull
    public static List<SavedStateMetadata> loadStateMetadata(@NonNull final Path stateDirectory) {

        final StringBuilder sb = new StringBuilder();
        sb.append("Validating streams against the following state snapshots:");

        final List<SavedStateInfo> stateInfo = SignedStateFileReader.getSavedStateFiles(stateDirectory);

        if (stateInfo.isEmpty()) {
            throw new InvalidStreamException("No saved state files found.");
        }

        final List<SavedStateMetadata> metadata = new ArrayList<>(stateInfo.size());
        for (final SavedStateInfo info : stateInfo) {
            metadata.add(info.metadata());
            sb.append("\n   - ").append(info.metadata());
        }

        logger.info(STARTUP.getMarker(), sb.toString());

        return metadata;
    }

    /**
     * Validate the consensus event stream. Returns a set containing descriptors for all events that were found in the
     * stream.
     *
     * @param states              metadata for all saved states in the state directory.
     * @param streamDirectory     the directory where stream files can be found.
     * @param permittedFileBreaks the permitted number of breaks in the file (usually just the number of reconnects).
     * @return a set containing descriptors for all events in the stream.
     */
    @NonNull
    public static Set<EventDescriptor> validateConsensusEventStream(
            @NonNull final List<SavedStateMetadata> states,
            @NonNull final Path streamDirectory,
            final int permittedFileBreaks) {

        final Set<EventDescriptor> descriptors = new HashSet<>();

        // TODO

        return descriptors;
    }

    /**
     * Validate a single CES file for internal consistency.
     *
     * @param file the file to validate
     * @return true if the file is valid, false otherwise.
     */
    public static boolean validateConsensusEventStreamFile(@NonNull final ConsensusEventStreamFileContents file) {
        // TODO recompute running hash
        // TODO verify signature
        // TODO verify topological ordering
        // TODO verify consensus order
        // TODO verify consensus timestamps (between events and against file timestamp)
        // TODO verify event signatures
        return false;
    }

    /**
     * Validate that two CES files are linked together correctly.
     *
     * @param firstFile  a CES file
     * @param secondFile the following CES file
     * @return true if the files are linked correctly, false otherwise.
     */
    public static boolean verifyConsensusEventFileLinkage(
            @NonNull final ConsensusEventStreamFileContents firstFile,
            @NonNull final ConsensusEventStreamFileContents secondFile) {

        // TODO verify hash
        // TODO verify consensus order between first and last event
        // TODO verify consensus timestamps between first and last event
        // TODO verify that a new file should have been started

        return false;
    }

    /**
     * Validate the preconsensus event stream. Returns a set containing descriptors for all events that were found in
     * the stream.
     *
     * @return @return a set containing descriptors for all events in the stream.
     */
    @NonNull
    public static Set<EventDescriptor> validatePreconsensusEventStream(
            @NonNull final Cryptography cryptography,
            @NonNull final List<SavedStateMetadata> states,
            @NonNull final Path streamDirectory)
            throws IOException {

        final Set<EventDescriptor> descriptors = new HashSet<>();

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
                    Last PCES file: {}""",
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
                discontinuityCount++;
            }

            validatePreconsensusEventFile(cryptography, file, descriptors, parents);
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
                descriptors.size(),
                discontinuityCount);

        return descriptors;
    }

    /**
     * Validate the contents of a PCES file and add descriptors of its events to the given set.
     *
     * @param file        the file to validate
     * @param descriptors the set to add descriptors to
     * @param parents     a set of the hashes of all events that have been parent to at least one event in the stream.
     *                    Once a hash is added to the parent set, we should never encounter an event with that hash. If
     *                    we do, that means we received a parent event after one of its children.
     */
    private static void validatePreconsensusEventFile(
            @NonNull final Cryptography cryptography,
            @NonNull final PreconsensusEventFile file,
            @NonNull final Set<EventDescriptor> descriptors,
            @NonNull final Set<Hash> parents)
            throws IOException {

        final IOIterator<GossipEvent> iterator = new PreconsensusEventFileIterator(file, NO_MINIMUM_GENERATION);
        while (iterator.hasNext()) {
            final GossipEvent event = iterator.next();

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

            cryptography.digestSync(event.getHashedData());
            if (parents.contains(event.getHashedData().getHash())) {
                throw new InvalidStreamException(
                        "Event " + event.getHashedData().getHash() + " is a parent of a previous event in the stream, "
                                + "preconsensus event stream is not in topological order");
            }
            parents.add(event.getHashedData().getSelfParentHash());
            parents.add(event.getHashedData().getOtherParentHash());

            event.buildDescriptor();
            descriptors.add(event.getDescriptor());
        }
    }

    /**
     * Compare events in the preconsensus stream against the consensus stream to ensure and ensure that they are
     * consistent with each other.
     *
     * @param preconsensusEvents descriptors for all events in the preconsensus stream
     * @param consensusEvents    descriptors for all events in the consensus stream
     */
    public static void validatePreconsensusAgainstConsensusStreams(
            @NonNull final Set<EventDescriptor> preconsensusEvents,
            @NonNull final Set<EventDescriptor> consensusEvents) {

        for (final EventDescriptor descriptor : consensusEvents) {

            // TODO don't throw if PCES file has been deleted

            if (!preconsensusEvents.contains(descriptor)) {
                throw new InvalidStreamException(
                        "Consensus event " + descriptor.getHash() + " is not in the preconsensus stream.");
            }
        }
    }
}
