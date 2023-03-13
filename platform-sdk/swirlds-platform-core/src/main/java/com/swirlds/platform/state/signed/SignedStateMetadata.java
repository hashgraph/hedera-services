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
import java.util.List;

/**
 * Metadata about a signed state.
 *
 * @param round                       the round of the signed state, corresponds to
 *                                    {@link SignedStateMetadataFields#ROUND}
 * @param numberOfConsensusEvents     the number of consensus events, starting from genesis, that have been handled to
 *                                    create this state, corresponds to
 *                                    {@link SignedStateMetadataFields#NUMBER_OF_CONSENSUS_EVENTS}
 * @param consensusTimestamp          the consensus timestamp of this state, corresponds to
 *                                    {@link SignedStateMetadataFields#CONSENSUS_TIMESTAMP}
 * @param runningEventHash            the running hash of all events, starting from genesis, that have been handled to
 *                                    create this state, corresponds to
 *                                    {@link SignedStateMetadataFields#RUNNING_EVENT_HASH}
 * @param minimumGenerationNonAncient the minimum generation of non-ancient events after this state reached consensus,
 *                                    corresponds to {@link SignedStateMetadataFields#MINIMUM_GENERATION_NON_ANCIENT}
 * @param softwareVersion             the application software version that created this state, corresponds to
 *                                    {@link SignedStateMetadataFields#SOFTWARE_VERSION}
 * @param wallClockTime               the wall clock time when this state was written to disk, corresponds to
 *                                    {@link SignedStateMetadataFields#WALL_CLOCK_TIME}
 * @param nodeId                      the ID of the node that wrote this state to disk, corresponds to
 *                                    {@link SignedStateMetadataFields#NODE_ID}
 * @param signingNodes                a comma separated list of node IDs that signed this state, corresponds to
 *                                    {@link SignedStateMetadataFields#SIGNING_NODES}
 * @param signingStakeSum             the sum of all signing nodes' stakes, corresponds to
 *                                    {@link SignedStateMetadataFields#SIGNING_STAKE_SUM}
 * @param totalStake                  the total stake of all nodes in the network, corresponds to
 *                                    {@link SignedStateMetadataFields#TOTAL_STAKE}
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
            return null;
        }

        // TODO parse the file

        return null;
    }

    /**
     * Create a new signed state metadata object from the given signed state.
     *
     * @param signedState the signed state
     * @param selfId      the ID of the node that created the signed state
     * @param now         the current time
     * @return the signed state metadata
     */
    public static SignedStateMetadata create(
            final SignedState signedState,
            final long selfId,
            final Instant now) {

        final PlatformState platformState = signedState.getState().getPlatformState();
        final PlatformData platformData = platformState.getPlatformData();

        final List<Long> signingNodes = signedState.getSigSet().getSigningNodes();
        Collections.sort(signingNodes);

        return new SignedStateMetadata(
                signedState.getRound(),
                platformData.getNumEventsCons(),
                signedState.getConsensusTimestamp(),
                platformData.getHashEventsCons(),
                platformData.getMinimumGenerationNonAncient(),
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
        try (final FileWriter writer = new FileWriter(metadataFile.toFile())) {
            writer.write(SignedStateMetadataFields.ROUND + ": " +
                    round + "\n");
            writer.write(SignedStateMetadataFields.NUMBER_OF_CONSENSUS_EVENTS + ": " +
                    numberOfConsensusEvents + "\n");
            writer.write(SignedStateMetadataFields.CONSENSUS_TIMESTAMP + ": " +
                    consensusTimestamp + "\n");
            writer.write(SignedStateMetadataFields.RUNNING_EVENT_HASH + ": " +
                    runningEventHash + "\n");
            writer.write(SignedStateMetadataFields.MINIMUM_GENERATION_NON_ANCIENT + ": " +
                    minimumGenerationNonAncient + "\n");
            writer.write(SignedStateMetadataFields.SOFTWARE_VERSION + ": " +
                    convertToString(softwareVersion) + "\n");
            writer.write(SignedStateMetadataFields.WALL_CLOCK_TIME + ": " +
                    wallClockTime + "\n");
            writer.write(SignedStateMetadataFields.NODE_ID + ": " +
                    nodeId + "\n");
            writer.write(SignedStateMetadataFields.SIGNING_NODES + ": " +
                    StringFormattingUtils.formattedList(signingNodes.iterator()) + "\n");
            writer.write(SignedStateMetadataFields.SIGNING_STAKE_SUM + ": " +
                    signingStakeSum + "\n");
            writer.write(SignedStateMetadataFields.TOTAL_STAKE + ": " +
                    totalStake + "\n");
        }
    }
}
