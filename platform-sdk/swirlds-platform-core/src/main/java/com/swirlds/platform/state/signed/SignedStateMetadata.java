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

package com.swirlds.platform.state.signed;

import static com.swirlds.platform.consensus.RoundCalculationUtils.getMinGenNonAncient;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.formatting.StringFormattingUtils;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadata about a signed state.
 *
 * @param round                       the round of the signed state, corresponds to
 *                                    {@link SignedStateMetadataField#ROUND}
 * @param numberOfConsensusEvents     the number of consensus events, starting from genesis, that have been handled to
 *                                    create this state, corresponds to
 *                                    {@link SignedStateMetadataField#NUMBER_OF_CONSENSUS_EVENTS}
 * @param consensusTimestamp          the consensus timestamp of this state, corresponds to
 *                                    {@link SignedStateMetadataField#CONSENSUS_TIMESTAMP}
 * @param runningEventHash            the running hash of all events, starting from genesis, that have been handled to
 *                                    create this state, corresponds to
 *                                    {@link SignedStateMetadataField#RUNNING_EVENT_HASH}
 * @param minimumGenerationNonAncient the minimum generation of non-ancient events after this state reached consensus,
 *                                    corresponds to {@link SignedStateMetadataField#MINIMUM_GENERATION_NON_ANCIENT}
 * @param softwareVersion             the application software version that created this state, corresponds to
 *                                    {@link SignedStateMetadataField#SOFTWARE_VERSION}
 * @param wallClockTime               the wall clock time when this state was written to disk, corresponds to
 *                                    {@link SignedStateMetadataField#WALL_CLOCK_TIME}
 * @param nodeId                      the ID of the node that wrote this state to disk, corresponds to
 *                                    {@link SignedStateMetadataField#NODE_ID}
 * @param signingNodes                a comma separated list of node IDs that signed this state, corresponds to
 *                                    {@link SignedStateMetadataField#SIGNING_NODES}
 * @param signingStakeSum             the sum of all signing nodes' stakes, corresponds to
 *                                    {@link SignedStateMetadataField#SIGNING_STAKE_SUM}
 * @param totalStake                  the total stake of all nodes in the network, corresponds to
 *                                    {@link SignedStateMetadataField#TOTAL_STAKE}
 */
public record SignedStateMetadata(
        long round,
        long numberOfConsensusEvents,
        Instant consensusTimestamp,
        Hash runningEventHash,
        long minimumGenerationNonAncient,
        String softwareVersion,
        Instant wallClockTime,
        long nodeId,
        List<Long> signingNodes,
        long signingStakeSum,
        long totalStake) {

    /**
     * The standard file name for the signed state metadata file.
     */
    public static final String FILE_NAME = "stateMetadata.txt";

    /**
     * Parse the signed state metadata from the given file.
     *
     * @param metadataFile the file to parse
     * @return the signed state metadata
     */
    public static SignedStateMetadata parse(final Path metadataFile) {
        if (!Files.exists(metadataFile)) {
            // We must elegantly handle the case where the metadata file does not exist
            // until we have fully migrated all state snapshots in production environments.
            // TODO log warning
            return null;
        }

        final String fileString;
        try {
            fileString = Files.readString(metadataFile);
        } catch (final IOException e) {
            // TODO log error
            return null;
        }

        final Map<SignedStateMetadataField, String> keyValuePairs = new HashMap<>();
        final String[] lines = fileString.split("\n");
        for (final String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            final String[] keyValue = line.split(":");
            if (keyValue.length != 2) {
                // TODO log error
                return null;
            }

            final SignedStateMetadataField field;
            try {
                 field = SignedStateMetadataField.valueOf(keyValue[0].strip());
            } catch (final IllegalArgumentException e) {
                // TODO log warning
                continue;
            }

            keyValuePairs.put(field, keyValue[1].strip());
        }

        final long round;
        if (keyValuePairs.containsKey(SignedStateMetadataField.ROUND)) {
            round = Long.parseLong(keyValuePairs.get(SignedStateMetadataField.ROUND));
        } else {
            // TODO log error
            return null;
        }

        final long numberOfConsensusEvents;
        if (keyValuePairs.containsKey(SignedStateMetadataField.NUMBER_OF_CONSENSUS_EVENTS)) {
            numberOfConsensusEvents = Long.parseLong(
                    keyValuePairs.get(SignedStateMetadataField.NUMBER_OF_CONSENSUS_EVENTS));
        } else {
            // TODO log error
            return null;
        }

        final Instant consensusTimestamp;
        if (keyValuePairs.containsKey(SignedStateMetadataField.CONSENSUS_TIMESTAMP)) {
            consensusTimestamp = Instant.parse(keyValuePairs.get(SignedStateMetadataField.CONSENSUS_TIMESTAMP));
        } else {
            // TODO log error
            return null;
        }

        final Hash runningEventHash;
        if (keyValuePairs.containsKey(SignedStateMetadataField.RUNNING_EVENT_HASH)) {
            runningEventHash = null; // TODO logic to parse a hash
        } else {
            // TODO log error
            return null;
        }

        final long minimumGenerationNonAncient;
        if (keyValuePairs.containsKey(SignedStateMetadataField.MINIMUM_GENERATION_NON_ANCIENT)) {
            minimumGenerationNonAncient = Long.parseLong(
                    keyValuePairs.get(SignedStateMetadataField.MINIMUM_GENERATION_NON_ANCIENT));
        } else {
            // TODO log error
            return null;
        }

        final String softwareVersion;
        if (keyValuePairs.containsKey(SignedStateMetadataField.SOFTWARE_VERSION)) {
            softwareVersion = keyValuePairs.get(SignedStateMetadataField.SOFTWARE_VERSION);
        } else {
            // TODO log error
            return null;
        }

        final Instant wallClockTime;
        if (keyValuePairs.containsKey(SignedStateMetadataField.WALL_CLOCK_TIME)) {
            wallClockTime = Instant.parse(keyValuePairs.get(SignedStateMetadataField.WALL_CLOCK_TIME));
        } else {
            // TODO log error
            return null;
        }

        final long nodeId;
        if (keyValuePairs.containsKey(SignedStateMetadataField.NODE_ID)) {
            nodeId = Long.parseLong(keyValuePairs.get(SignedStateMetadataField.NODE_ID));
        } else {
            // TODO log error
            return null;
        }

        final List<Long> signingNodes;
        if (keyValuePairs.containsKey(SignedStateMetadataField.SIGNING_NODES)) {
            signingNodes = null; // TODO logic to parse a list of longs
        } else {
            // TODO log error
            return null;
        }

        final long signingStakeSum;
        if (keyValuePairs.containsKey(SignedStateMetadataField.SIGNING_STAKE_SUM)) {
            signingStakeSum = Long.parseLong(keyValuePairs.get(SignedStateMetadataField.SIGNING_STAKE_SUM));
        } else {
            // TODO log error
            return null;
        }

        final long totalStake;
        if (keyValuePairs.containsKey(SignedStateMetadataField.TOTAL_STAKE)) {
            totalStake = Long.parseLong(keyValuePairs.get(SignedStateMetadataField.TOTAL_STAKE));
        } else {
            // TODO log error
            return null;
        }

        // TODO catch parsing errors

        return null;
    }

    /**
     * Create a new signed state metadata object from the given signed state.
     *
     * @param signedState     the signed state
     * @param consensusConfig the consensus configuration
     * @param selfId          the ID of the node that created the signed state
     * @param now             the current time
     * @return the signed state metadata
     */
    public static SignedStateMetadata create(
            final SignedState signedState,
            final ConsensusConfig consensusConfig,
            final long selfId,
            final Instant now) {

        final PlatformState platformState = signedState.getState().getPlatformState();
        final PlatformData platformData = platformState.getPlatformData();

        final List<Long> signingNodes = signedState.getSigSet().getSigningNodes();
        Collections.sort(signingNodes);

        final int roundsNonAncient = consensusConfig.roundsNonAncient();
        final long minimumGenerationNonAncient = getMinGenNonAncient(roundsNonAncient, signedState);

        return new SignedStateMetadata(
                signedState.getRound(),
                platformData.getNumEventsCons(),
                signedState.getConsensusTimestamp(),
                platformData.getHashEventsCons(),
                minimumGenerationNonAncient,
                convertToString(platformData.getCreationSoftwareVersion()),
                now,
                selfId,
                signingNodes,
                signedState.getSigningStake(),
                platformState.getAddressBook().getTotalStake());
    }

    /**
     * Convert an object to a string, throw if the string has newlines.
     *
     * @param value the object to convert
     * @return the string representation of the object
     */
    private static String convertToString(final Object value) {
        final String string = value == null ? "null" : value.toString();

        if (string.contains("\n")) {
            throw new IllegalArgumentException("Value cannot contain newlines: " + value);
        }
        return string;
    }

    /**
     * Write the signed state metadata to the given file.
     *
     * @param metadataFile the file to write to
     * @throws IOException if an error occurs while writing
     */
    public void write(final Path metadataFile) throws IOException {

        // TODO convert to a map of string -> string and then write it using a utility method

        try (final FileWriter writer = new FileWriter(metadataFile.toFile())) {
            writer.write(SignedStateMetadataField.ROUND + ": " +
                    round + "\n");
            writer.write(SignedStateMetadataField.NUMBER_OF_CONSENSUS_EVENTS + ": " +
                    numberOfConsensusEvents + "\n");
            writer.write(SignedStateMetadataField.CONSENSUS_TIMESTAMP + ": " +
                    consensusTimestamp + "\n");
            writer.write(SignedStateMetadataField.RUNNING_EVENT_HASH + ": " +
                    runningEventHash + "\n");
            writer.write(SignedStateMetadataField.MINIMUM_GENERATION_NON_ANCIENT + ": " +
                    minimumGenerationNonAncient + "\n");
            writer.write(SignedStateMetadataField.SOFTWARE_VERSION + ": " +
                    convertToString(softwareVersion) + "\n");
            writer.write(SignedStateMetadataField.WALL_CLOCK_TIME + ": " +
                    wallClockTime + "\n");
            writer.write(SignedStateMetadataField.NODE_ID + ": " +
                    nodeId + "\n");
            writer.write(SignedStateMetadataField.SIGNING_NODES + ": " +
                    StringFormattingUtils.formattedList(signingNodes.iterator()) + "\n");
            writer.write(SignedStateMetadataField.SIGNING_STAKE_SUM + ": " +
                    signingStakeSum + "\n");
            writer.write(SignedStateMetadataField.TOTAL_STAKE + ": " +
                    totalStake + "\n");
        }
    }
}
