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
import static com.swirlds.platform.event.stream.validation.ConsensusEventStreamValidation.validateConsensusEventStream;
import static com.swirlds.platform.event.stream.validation.PreconsensusEventStreamValidation.validatePreconsensusEventStream;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.platform.event.EventDescriptor;
import com.swirlds.platform.event.validation.InvalidStreamException;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SavedStateMetadata;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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

        final Set<EventDescriptor> preconsensusEvents = validatePreconsensusEventStream(
                cryptography, states, preconsensusEventStreamDirectory, permittedFileBreaks);

        final Set<EventDescriptor> consensusEvents =
                validateConsensusEventStream(cryptography, states, consensusEventStreamDirectory, permittedFileBreaks);

        validatePreconsensusAgainstConsensusStreams(states, preconsensusEvents, consensusEvents);

        logger.info(STARTUP.getMarker(), "Event streams pass validation.");
    }

    /**
     * Load the metadata for all saved states in the state directory.
     *
     * @param stateDirectory the root of the directory tree where state files are saved.
     * @return a list of metadata for all saved states in the state directory ordered from most recent to least recent.
     */
    @NonNull
    private static List<SavedStateMetadata> loadStateMetadata(@NonNull final Path stateDirectory) {

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
     * Compare events in the preconsensus stream against the consensus stream to ensure and ensure that they are
     * consistent with each other.
     *
     * @param states             metadata for all saved states in the state directory. Most recent state is first in the
     *                           last, oldest state is last.
     * @param preconsensusEvents descriptors for all events in the preconsensus stream
     * @param consensusEvents    descriptors for all events in the consensus stream
     */
    private static void validatePreconsensusAgainstConsensusStreams(
            @NonNull final List<SavedStateMetadata> states,
            @NonNull final Set<EventDescriptor> preconsensusEvents,
            @NonNull final Set<EventDescriptor> consensusEvents) {

        // Don't validate events with smaller generations since we may have deleted the corresponding PCES file.
        final long oldestStateNonAncientGeneration =
                states.get(states.size() - 1).minimumGenerationNonAncient();

        for (final EventDescriptor descriptor : consensusEvents) {

            if (descriptor.getGeneration() < oldestStateNonAncientGeneration) {
                // PCES file may have been deleted, do not validate
                continue;
            }

            if (!preconsensusEvents.contains(descriptor)) {
                throw new InvalidStreamException(
                        "Consensus event " + descriptor.getHash() + " is not in the preconsensus stream.");
            }
        }

        logger.info(STARTUP.getMarker(), "Preconsensus and consensus streams are consistent with each other.");
    }
}
