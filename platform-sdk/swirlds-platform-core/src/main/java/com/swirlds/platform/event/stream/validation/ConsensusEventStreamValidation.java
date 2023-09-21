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

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.parseSigFile;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.base.utility.Pair;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.stream.RunningHashCalculatorForStream;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.platform.event.EventDescriptor;
import com.swirlds.platform.event.validation.InvalidStreamException;
import com.swirlds.platform.recovery.internal.EventStreamSingleFileIterator;
import com.swirlds.platform.state.signed.SavedStateMetadata;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for validating the consensus event stream.
 */
public final class ConsensusEventStreamValidation {

    private static final Logger logger = LogManager.getLogger(ConsensusEventStreamValidation.class);

    private ConsensusEventStreamValidation() {}

    /**
     * Validate the consensus event stream. Returns a set containing descriptors for all events that were found in the
     * stream.
     *
     * @param cryptography             the cryptography object to use for hashing
     * @param states                   metadata for all saved states in the state directory.
     * @param streamDirectory          the directory where stream files can be found.
     * @param permittedDiscontinuities the permitted number of breaks in the file (usually just the number of
     *                                 reconnects).
     * @return a set containing descriptors for all events in the stream.
     */
    @NonNull
    public static Set<EventDescriptor> validateConsensusEventStream(
            @NonNull final Cryptography cryptography,
            @NonNull final List<SavedStateMetadata> states,
            @NonNull final Path streamDirectory,
            final int permittedDiscontinuities)
            throws IOException {

        final List<Path> files = getConsensusEventFiles(streamDirectory);

        if (files.isEmpty()) {
            throw new InvalidStreamException("No consensus event files found.");
        }

        logger.info(
                STARTUP.getMarker(),
                """
                        Found {} consensus event files.
                            First CES file: {}
                            Last CES file:  {}""",
                files.size(),
                files.get(0),
                files.get(files.size() - 1));

        // Parse each file and validate that the file is internally consistent.
        final List<ConsensusEventStreamFileContents> fileContents = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            final Path file = files.get(index);

            final ConsensusEventStreamFileContents contents = validateConsensusEventStreamFile(cryptography, file);
            if (contents == null) {
                // Ignore files that are malformed (possible when a node crashes).
                continue;
            }
            fileContents.add(contents);
        }

        // Check for discontinuities
        int discontinuties = 0;
        for (int i = 1; i < fileContents.size(); i++) {
            final boolean contiguous = areFilesContiguous(fileContents.get(i - 1), fileContents.get(i));

            if (!contiguous) {
                discontinuties++;
            }
        }
        if (discontinuties > permittedDiscontinuities) {
            throw new InvalidStreamException(
                    "Found %d file breaks in the consensus event stream, but only %d are permitted."
                            .formatted(discontinuties, permittedDiscontinuities));
        }

        final Set<EventDescriptor> descriptors = new HashSet<>();
        for (final ConsensusEventStreamFileContents contents : fileContents) {
            descriptors.addAll(contents.descriptors());
        }

        validateConsensusStreamAgainstStates(states, fileContents);

        logger.info(
                STARTUP.getMarker(),
                "Consensus event stream is valid. {} files contained {} events. Discontinuity count: {}.",
                files.size(),
                descriptors.size(),
                discontinuties);

        return descriptors;
    }

    /**
     * For each state (except perhaps the last state), we should find a CES event stream file that starts with the
     * running event hash defined by the state.
     *
     * @param states       the states
     * @param fileContents the file contents
     */
    private static void validateConsensusStreamAgainstStates(
            @NonNull final List<SavedStateMetadata> states,
            @NonNull final List<ConsensusEventStreamFileContents> fileContents) {

        // FUTURE WORK: we can't actually do this validation until we properly align stream files with state saving.
        //              This is just a place holder.

    }

    /**
     * Get a list of all consensus event stream files.
     *
     * @param streamDirectory the directory where stream files can be found.
     * @return a list of CES files
     */
    @NonNull
    private static List<Path> getConsensusEventFiles(@NonNull final Path streamDirectory) throws IOException {

        final List<Path> files = new ArrayList<>();
        try (final Stream<Path> stream = Files.walk(streamDirectory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".evts"))
                    .sorted()
                    .forEach(files::add);
        }

        return files;
    }

    /**
     * Parse the timestamp from a CES file name. File is in the format 2023-09-19T19_36_00.068694000Z.evts
     *
     * @param consensusEventStreamFile the path to the file
     * @return the timestamp
     */
    @NonNull
    private static Instant parseTimestamp(@NonNull final Path consensusEventStreamFile) {
        final String instantString = consensusEventStreamFile
                .getFileName()
                .toString()
                .replace(".evts", "")
                .replace("_", ":");

        return Instant.parse(instantString);
    }

    /**
     * Validate a single CES file for internal consistency.
     *
     * @param cryptography the cryptography object to use for hashing
     * @param path         the path to the file
     * @return the contents of the file, or null if the file is a partial file (either missing a signature or not
     * properly closed)
     */
    @Nullable
    private static ConsensusEventStreamFileContents validateConsensusEventStreamFile(
            @NonNull final Cryptography cryptography, @NonNull final Path path) throws IOException {

        final Path signaturePath = Path.of(path + "_sig");

        final Signature signature = parseSignatureFile(signaturePath);
        if (signature == null) {
            // A file without a signature file is not complete.
            return null;
        }

        final Instant fileTimestamp = parseTimestamp(path);

        // Parse the events.
        final List<DetailedConsensusEvent> events = new ArrayList<>();
        final EventStreamSingleFileIterator iterator = new EventStreamSingleFileIterator(path, false);
        while (iterator.hasNext()) {
            final DetailedConsensusEvent event = iterator.next();
            cryptography.digestSync(event.getBaseEventHashedData());
            events.add(event);
        }

        // Get the start/end hashes and validate the running hash.
        final Hash startHash = iterator.getStartHash();
        if (startHash == null) {
            if (events.isEmpty()) {
                // we crashed right when we started writing this file, or th
                return null;
            }
            // It's illegal to have a null starting hash
            throw new InvalidStreamException("CES file " + path + " has a null starting hash.");
        }
        final Hash endHash = iterator.getEndHash();
        if (endHash == null) {
            // File was not properly closed.
            return null;
        }
        validateRunningHash(startHash, endHash, events);

        // It shouldn't be possible to create a file with zero events unless we crashed and didn't write an ending hash.
        if (events.isEmpty()) {
            throw new InvalidStreamException("CES file " + path + " has no events.");
        }

        // Make sure that events are in the proper order within the stream.
        for (int index = 1; index < events.size(); index++) {
            validateAdjacentEvents(events.get(index - 1), events.get(index));
        }

        // No event should have a timestamp less than the file timestamp.
        for (final DetailedConsensusEvent event : events) {
            if (event.getConsensusData().getConsensusTimestamp().isBefore(fileTimestamp)) {
                throw new InvalidStreamException(
                        "Event " + event.getBaseEventHashedData().getHash() + " has consensus timestamp "
                                + event.getConsensusData().getConsensusTimestamp() + " which is before the file "
                                + "timestamp " + fileTimestamp);
            }
        }

        if (!events.get(0).getConsensusData().getConsensusTimestamp().equals(fileTimestamp)) {
            throw new InvalidStreamException("CES file " + path + " has a timestamp of " + fileTimestamp
                    + " but the first event has consensus timestamp "
                    + events.get(0).getConsensusData().getConsensusTimestamp()
                    + ". File is improperly named.");
        }

        // FUTURE WORK: validate signatures (it's currently really complicated to instantiate a Crypto object)

        return new ConsensusEventStreamFileContents(
                startHash,
                endHash,
                convertToDescriptors(events),
                events.get(0),
                events.get(events.size() - 1),
                fileTimestamp);
    }

    /**
     * Make sure two adjacent events are in the proper order in the CES file.
     *
     * @param firstEvent  the first event
     * @param secondEvent the event immediately after the first event
     */
    private static void validateAdjacentEvents(
            @NonNull final DetailedConsensusEvent firstEvent, @NonNull final DetailedConsensusEvent secondEvent) {
        if (firstEvent.getConsensusData().getConsensusOrder() + 1
                != secondEvent.getConsensusData().getConsensusOrder()) {
            throw new InvalidStreamException(
                    "Event " + secondEvent.getBaseEventHashedData().getHash() + " has consensus order "
                            + secondEvent.getConsensusData().getConsensusOrder() + " but the previous event has "
                            + "consensus order " + firstEvent.getConsensusData().getConsensusOrder()
                            + ". Consensus event stream is not in consensus order.");
        }

        if (!firstEvent
                .getConsensusData()
                .getConsensusTimestamp()
                .isBefore(secondEvent.getConsensusData().getConsensusTimestamp())) {
            throw new InvalidStreamException("Event "
                    + secondEvent.getBaseEventHashedData().getHash() + " has consensus timestamp "
                    + secondEvent.getConsensusData().getConsensusTimestamp() + " but the previous event has "
                    + "consensus timestamp " + firstEvent.getConsensusData().getConsensusTimestamp()
                    + ". Consensus event stream is not in consensus order.");
        }
    }

    /**
     * Validate that the running hash of a CES file is correct.
     *
     * @param startHash the running hash of the previous file
     * @param endHash   the running hash of the current file
     * @param events    the events in the file
     */
    private static void validateRunningHash(
            @NonNull final Hash startHash,
            @NonNull final Hash endHash,
            @NonNull final List<DetailedConsensusEvent> events) {

        final RunningHashCalculatorForStream<DetailedConsensusEvent> calculator =
                new RunningHashCalculatorForStream<>();

        calculator.setRunningHash(startHash);

        for (final DetailedConsensusEvent event : events) {
            calculator.addObject(event);
        }

        final Hash calculatedEndHash = calculator.getRunningHash();
        if (!calculatedEndHash.equals(endHash)) {
            throw new InvalidStreamException(
                    "CES file has incorrect running hash. Expected " + endHash + " but got " + calculatedEndHash);
        }
    }

    /**
     * Parse the signature file
     *
     * @param signaturePath the path to the signature file
     * @return the signature, or null if the file can't be parsed
     */
    @Nullable
    private static Signature parseSignatureFile(@NonNull final Path signaturePath) {
        if (!Files.exists(signaturePath)) {
            return null;
        }

        try {
            final Pair<Pair<Hash, Signature>, Pair<Hash, Signature>> signatureData =
                    parseSigFile(signaturePath.toFile(), EventStreamType.getInstance());

            return signatureData.left().right();

        } catch (final IOException e) {
            return null;
        }
    }

    /**
     * Convert a list of events to a list of event descriptors
     *
     * @param events the events
     * @return the descriptors
     */
    @NonNull
    private static List<EventDescriptor> convertToDescriptors(@NonNull final List<DetailedConsensusEvent> events) {
        final List<EventDescriptor> descriptors = new ArrayList<>(events.size());
        for (final DetailedConsensusEvent event : events) {
            descriptors.add(new EventDescriptor(
                    event.getBaseEventHashedData().getHash(),
                    event.getBaseEventHashedData().getCreatorId(),
                    event.getBaseEventHashedData().getGeneration()));
        }
        return descriptors;
    }

    /**
     * Check if two files are contiguous.
     *
     * @param firstFile  a CES file
     * @param secondFile the following CES file
     * @return true if the files are contiguous, false otherwise
     */
    private static boolean areFilesContiguous(
            @NonNull final ConsensusEventStreamFileContents firstFile,
            @NonNull final ConsensusEventStreamFileContents secondFile) {

        if (!firstFile.endingHash().equals(secondFile.beginningHash())) {
            // Files aren't linked. If these hashes do match, it is an error if the other data isn't also linked.
            return false;
        }

        final DetailedConsensusEvent leftEvent = firstFile.lastEvent();
        final DetailedConsensusEvent rightEvent = secondFile.firstEvent();

        if (leftEvent.getConsensusData().getConsensusOrder() + 1
                != rightEvent.getConsensusData().getConsensusOrder()) {
            throw new InvalidStreamException(
                    "Event " + rightEvent.getBaseEventHashedData().getHash() + " has consensus order "
                            + rightEvent.getConsensusData().getConsensusOrder() + " but the previous event has "
                            + "consensus order " + leftEvent.getConsensusData().getConsensusOrder()
                            + ". Consensus event stream is not in consensus order.");
        }

        if (!leftEvent
                .getConsensusData()
                .getConsensusTimestamp()
                .isBefore(rightEvent.getConsensusData().getConsensusTimestamp())) {
            throw new InvalidStreamException("Event "
                    + rightEvent.getBaseEventHashedData().getHash() + " has consensus timestamp "
                    + rightEvent.getConsensusData().getConsensusTimestamp() + " but the previous event has "
                    + "consensus timestamp " + leftEvent.getConsensusData().getConsensusTimestamp()
                    + ". Consensus event stream is not in consensus order.");
        }

        if (!leftEvent.getConsensusData().getConsensusTimestamp().isBefore(secondFile.timestamp())) {
            throw new InvalidStreamException("CES file " + secondFile + " has a timestamp of " + secondFile.timestamp()
                    + " but the previous event has consensus timestamp "
                    + leftEvent.getConsensusData().getConsensusTimestamp()
                    + ". File boundaries are improperly defined.");
        }

        return true;
    }
}
