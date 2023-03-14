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

import static com.swirlds.common.formatting.StringFormattingUtils.formattedList;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.consensus.RoundCalculationUtils.getMinGenNonAncient;
import static com.swirlds.platform.state.signed.SignedStateMetadataField.CONSENSUS_TIMESTAMP;
import static com.swirlds.platform.state.signed.SignedStateMetadataField.MINIMUM_GENERATION_NON_ANCIENT;
import static com.swirlds.platform.state.signed.SignedStateMetadataField.NODE_ID;
import static com.swirlds.platform.state.signed.SignedStateMetadataField.NUMBER_OF_CONSENSUS_EVENTS;
import static com.swirlds.platform.state.signed.SignedStateMetadataField.ROUND;
import static com.swirlds.platform.state.signed.SignedStateMetadataField.RUNNING_EVENT_HASH;
import static com.swirlds.platform.state.signed.SignedStateMetadataField.SIGNING_NODES;
import static com.swirlds.platform.state.signed.SignedStateMetadataField.SIGNING_STAKE_SUM;
import static com.swirlds.platform.state.signed.SignedStateMetadataField.SOFTWARE_VERSION;
import static com.swirlds.platform.state.signed.SignedStateMetadataField.TOTAL_STAKE;
import static com.swirlds.platform.state.signed.SignedStateMetadataField.WALL_CLOCK_TIME;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Metadata about a signed state. Fields in this record may be null if they are not present in the metadata file.
 * All fields in this record will be null if the metadata file is missing.
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
public record SignedStateMetadata( // TODO test this
        Long round,
        Long numberOfConsensusEvents,
        Instant consensusTimestamp,
        Hash runningEventHash,
        Long minimumGenerationNonAncient,
        String softwareVersion,
        Instant wallClockTime,
        Long nodeId,
        List<Long> signingNodes,
        Long signingStakeSum,
        Long totalStake) {

    /**
     * The standard file name for the signed state metadata file.
     */
    public static final String FILE_NAME = "stateMetadata.txt";

    private static final Logger logger = LogManager.getLogger(SignedStateMetadata.class);

    /**
     * Parse the signed state metadata from the given file.
     *
     * @param metadataFile the file to parse
     * @return the signed state metadata
     */
    public static SignedStateMetadata parse(final Path metadataFile) {
        final Map<SignedStateMetadataField, String> data = parseStringMap(metadataFile);
        return new SignedStateMetadata(
                parseLong(data, ROUND),
                parseLong(data, NUMBER_OF_CONSENSUS_EVENTS),
                parseInstant(data, CONSENSUS_TIMESTAMP),
                parseHash(data, RUNNING_EVENT_HASH),
                parseLong(data, MINIMUM_GENERATION_NON_ANCIENT),
                parseString(data, SOFTWARE_VERSION),
                parseInstant(data, WALL_CLOCK_TIME),
                parseLong(data, NODE_ID),
                parseLongList(data, SIGNING_NODES),
                parseLong(data, SIGNING_STAKE_SUM),
                parseLong(data, TOTAL_STAKE));
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
     * Parse the key/value pairs written to disk. The inverse of {@link #buildStringMap()}.
     */
    private static Map<SignedStateMetadataField, String> parseStringMap(final Path metadataFile) {

        if (!Files.exists(metadataFile)) {
            // We must elegantly handle the case where the metadata file does not exist
            // until we have fully migrated all state snapshots in production environments.
            logger.warn(STARTUP.getMarker(), "Signed state does not have a metadata file at {}", metadataFile);
            return new HashMap<>();
        }

        try {
            final Map<SignedStateMetadataField, String> map = new HashMap<>();

            try (final BufferedReader reader = new BufferedReader(new FileReader(metadataFile.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String[] parts = line.split(":");
                    if (parts.length != 2) {
                        logger.warn(STARTUP.getMarker(), "Invalid line in metadata file: {}", line);
                        continue;
                    }
                    try {
                        final SignedStateMetadataField key = SignedStateMetadataField.valueOf(parts[0].strip());
                        final String value = parts[1].strip();
                        map.put(key, value);
                    } catch (final IllegalArgumentException e) {
                        logger.warn(STARTUP.getMarker(), "Invalid key in metadata file: {}", parts[0].strip());
                    }
                }
            }

            if (map.size() != SignedStateMetadataField.values().length) {
                throw new IOException("Invalid number of lines in metadata file: " + map.size());
            }

            return map;
        } catch (final IOException e) {
            logger.warn(STARTUP.getMarker(), "Failed to parse signed state metadata file: {}", metadataFile, e);
            return new HashMap<>();
        }
    }

    /**
     * Write a log message for a missing field.
     *
     * @param field the missing field
     */
    private static void logMissingField(final SignedStateMetadataField field) {
        logger.warn(STARTUP.getMarker(), "Signed state metadata file is missing field: {}", field);
    }

    /**
     * Write a log message for an invalid field.
     *
     * @param field the invalid field
     * @param value the invalid value
     * @param e     the exception
     */
    private static void logInvalidField(final SignedStateMetadataField field, final String value, final Exception e) {
        logger.warn(
                STARTUP.getMarker(), "Signed state metadata file has invalid value for field {}: {}", field, value, e);
    }

    /**
     * Attempt to parse a long from the data map.
     *
     * @param data  the data map
     * @param field the field to parse
     * @return the parsed long, or null if the field is not present or the value is not a valid long
     */
    private static Long parseLong(
            final Map<SignedStateMetadataField, String> data, final SignedStateMetadataField field) {

        if (!data.containsKey(field)) {
            logMissingField(field);
            return null;
        }

        final String value = data.get(field);
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException e) {
            logInvalidField(field, value, e);
            return null;
        }
    }

    /**
     * Attempt to parse a string from the data map.
     *
     * @param data  the data map
     * @param field the field to parse
     * @return the parsed string, or null if the field is not present or the value is not a valid hash
     */
    @SuppressWarnings("SameParameterValue")
    private static String parseString(
            final Map<SignedStateMetadataField, String> data, final SignedStateMetadataField field) {

        if (!data.containsKey(field)) {
            logMissingField(field);
            return null;
        }

        return data.get(field);
    }

    /**
     * Attempt to parse an instant from the data map.
     *
     * @param data  the data map
     * @param field the field to parse
     * @return the parsed instant, or null if the field is not present or the value is not a valid instant
     */
    private static Instant parseInstant(
            final Map<SignedStateMetadataField, String> data, final SignedStateMetadataField field) {

        if (!data.containsKey(field)) {
            logMissingField(field);
            return null;
        }

        final String value = data.get(field);
        try {
            return Instant.parse(value);
        } catch (final DateTimeParseException e) {
            logInvalidField(field, value, e);
            return null;
        }
    }

    /**
     * Attempt to parse a list of longs from the data map.
     *
     * @param data  the data map
     * @param field the field to parse
     * @return the parsed list of longs, or null if the field is not present or the value is not a valid list of longs
     */
    @SuppressWarnings("SameParameterValue")
    private static List<Long> parseLongList(
            final Map<SignedStateMetadataField, String> data, final SignedStateMetadataField field) {

        if (!data.containsKey(field)) {
            logMissingField(field);
            return null;
        }

        final String value = data.get(field);
        final String[] parts = value.split(",");
        final List<Long> list = new ArrayList<>();
        for (final String part : parts) {
            try {
                list.add(Long.parseLong(part));
            } catch (final NumberFormatException e) {
                logInvalidField(field, value, e);
                return null;
            }
        }
        return list;
    }

    /**
     * Attempt to parse a hash from the data map.
     *
     * @param data  the data map
     * @param field the field to parse
     * @return the parsed hash, or null if the field is not present or the value is not a valid hash
     */
    @SuppressWarnings("SameParameterValue")
    private static Hash parseHash(
            final Map<SignedStateMetadataField, String> data, final SignedStateMetadataField field) {

        if (!data.containsKey(field)) {
            logMissingField(field);
            return null;
        }

        final String value = data.get(field);
        try {
            return new Hash(unhex(value));
        } catch (final IllegalArgumentException e) {
            logInvalidField(field, value, e);
            return null;
        }
    }

    /**
     * Build a map of key/value pairs to be written to disk.
     */
    private Map<SignedStateMetadataField, String> buildStringMap() {
        final Map<SignedStateMetadataField, String> map = new HashMap<>();

        map.put(ROUND, Long.toString(round));
        map.put(NUMBER_OF_CONSENSUS_EVENTS, Long.toString(numberOfConsensusEvents));
        map.put(CONSENSUS_TIMESTAMP, consensusTimestamp.toString());
        map.put(RUNNING_EVENT_HASH, runningEventHash.toString());
        map.put(MINIMUM_GENERATION_NON_ANCIENT, Long.toString(minimumGenerationNonAncient));
        map.put(SOFTWARE_VERSION, softwareVersion);
        map.put(WALL_CLOCK_TIME, wallClockTime.toString());
        map.put(NODE_ID, Long.toString(nodeId));
        map.put(SIGNING_NODES, formattedList(signingNodes.iterator()));
        map.put(SIGNING_STAKE_SUM, Long.toString(signingStakeSum));
        map.put(TOTAL_STAKE, Long.toString(totalStake));

        return map;
    }

    /**
     * Write the signed state metadata to the given file.
     *
     * @param metadataFile the file to write to
     * @throws IOException if an error occurs while writing
     */
    public void write(final Path metadataFile) throws IOException {

        final Map<SignedStateMetadataField, String> map = buildStringMap();
        final List<SignedStateMetadataField> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);

        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(metadataFile.toFile()))) {
            for (final SignedStateMetadataField key : keys) {
                final String value = map.get(key);

                writer.write(key + ": " + value + "\n");
            }
        }
    }
}
