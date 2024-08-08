/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.merkle;

import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_ACCOUNTS;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_ALIASES;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_BLOCK_INFO;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_BLOCK_STREAM_INFO;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_CONGESTION_STARTS;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_CONTRACT_BYTECODE;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_CONTRACT_STORAGE;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_ENTITY_ID;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_FILES;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_FREEZE_TIME;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_MIDNIGHT_RATES;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_NETWORK_REWARDS;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_NFTS;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_NODES;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_PENDING_AIRDROPS;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_RECORD_QUEUE;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_RUNNING_HASHES;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_SCHEDULES_BY_EQUALITY;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_SCHEDULES_BY_EXPIRY;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_SCHEDULES_BY_ID;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_STAKING_INFO;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_THROTTLE_USAGE;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_TOKENS;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_TOKEN_RELATIONS;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_TOPICS;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_DATA_150;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_DATA_151;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_DATA_152;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_DATA_153;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_DATA_154;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_DATA_155;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_DATA_156;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_DATA_157;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_DATA_158;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_DATA_159;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_FILE;
import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_UPGRADE_FILE_HASH;
import static com.swirlds.common.utility.CommonUtils.getNormalisedStringBytes;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.utility.NonCryptographicHashing;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/** Utility class for working with states. */
public final class StateUtils {
    /** Prevent instantiation */
    private StateUtils() {}

    /**
     * Write the {@code object} to the {@link OutputStream} using the given {@link Codec}.
     *
     * @param out The object to write out
     * @param codec The codec to use. MUST be compatible with the {@code object} type
     * @param object The object to write
     * @return The number of bytes written to the stream.
     * @param <T> The type of the object and associated codec.
     * @throws IOException If the output stream throws it.
     * @throws ClassCastException If the object or codec is not for type {@code T}.
     */
    public static <T> int writeToStream(
            @NonNull final OutputStream out, @NonNull final Codec<T> codec, @NonNull final T object)
            throws IOException {
        final var byteStream = new ByteArrayOutputStream();
        codec.write(object, new WritableStreamingData(byteStream));

        final var stream = new WritableStreamingData(out);
        stream.writeInt(byteStream.size());
        stream.writeBytes(byteStream.toByteArray());
        return byteStream.size();
    }

    /**
     * Read an object from the {@link InputStream} using the given {@link Codec}.
     *
     * @param in The input stream to read from
     * @param codec The codec to use. MUST be compatible with the {@code object} type
     * @return The object read from the stream
     * @param <T> The type of the object and associated codec.
     * @throws IOException If the input stream throws it or parsing fails
     * @throws ClassCastException If the object or codec is not for type {@code T}.
     */
    @NonNull
    public static <T> T readFromStream(@NonNull final InputStream in, @NonNull final Codec<T> codec)
            throws IOException {
        final var stream = new ReadableStreamingData(in);
        final var size = stream.readInt();
        stream.limit((long) size + Integer.BYTES); // +4 for the size
        try {
            return codec.parse(stream);
        } catch (final ParseException ex) {
            throw new IOException(ex);
        }
    }

    /**
     * Verifies the service name meets all the validation requirements.
     *
     * @param serviceName The service name
     * @return The service name provided as the argument
     * @throws NullPointerException if the service name is null
     * @throws IllegalArgumentException if any other validation criteria fails
     */
    @NonNull
    public static String validateServiceName(@NonNull final String serviceName) {
        if (Objects.requireNonNull(serviceName).isEmpty()) {
            throw new IllegalArgumentException("The service name must have characters");
        }

        return validateIdentifier(serviceName);
    }

    /**
     * Verifies the state key meets all the validation requirements.
     *
     * @param stateKey The state key
     * @return The state key provided as the argument
     * @throws NullPointerException if the state key is null
     * @throws IllegalArgumentException if any other validation criteria fails
     */
    @NonNull
    public static String validateStateKey(@NonNull final String stateKey) {
        if (Objects.requireNonNull(stateKey).isEmpty()) {
            throw new IllegalArgumentException("The state key must have characters");
        }

        return validateIdentifier(stateKey);
    }

    /**
     * Verifies the identifier meets all the validation requirements.
     *
     * @param stateKey The identifier
     * @return The identifier provided as the argument
     * @throws NullPointerException if the identifier is null
     * @throws IllegalArgumentException if any other validation criteria fails
     */
    @NonNull
    static String validateIdentifier(@NonNull final String stateKey) {
        if (Objects.requireNonNull(stateKey).isEmpty()) {
            throw new IllegalArgumentException("The identifier must have characters");
        }

        for (int i = 0; i < stateKey.length(); i++) {
            final var c = stateKey.charAt(i);
            if (!isAsciiUnderscoreOrDash(c) && !isAsciiLetter(c) && !isAsciiNumber(c)) {
                throw new IllegalArgumentException("Illegal character '" + c + "' at position " + i);
            }
        }

        return stateKey;
    }

    /**
     * Computes the label for a merkle node given the service name and state key
     *
     * @param serviceName The service name
     * @param stateKey The state key
     * @return The computed label
     */
    public static String computeLabel(@NonNull final String serviceName, @NonNull final String stateKey) {
        return Objects.requireNonNull(serviceName) + "." + Objects.requireNonNull(stateKey);
    }

    /**
     * Given the state name, returns the canonical identifier of that state.
     * @param stateName The state name
     * @return The canonical identifier of the state
     */
    public static int stateIdentifierOf(@NonNull final String stateName) {
        return switch (stateName) {
            case "AddressBookService.NODES" -> STATE_ID_NODES.protoOrdinal();
            case "BlockRecordService.BLOCKS" -> STATE_ID_BLOCK_INFO.protoOrdinal();
            case "BlockRecordService.RUNNING_HASHES" -> STATE_ID_RUNNING_HASHES.protoOrdinal();
            case "BlockStreamService.BLOCK_STREAM_INFO" -> STATE_ID_BLOCK_STREAM_INFO.protoOrdinal();
            case "CongestionThrottleService.CONGESTION_LEVEL_STARTS" -> STATE_ID_CONGESTION_STARTS.protoOrdinal();
            case "CongestionThrottleService.THROTTLE_USAGE_SNAPSHOTS" -> STATE_ID_THROTTLE_USAGE.protoOrdinal();
            case "ConsensusService.TOPICS" -> STATE_ID_TOPICS.protoOrdinal();
            case "ContractService.BYTECODE" -> STATE_ID_CONTRACT_BYTECODE.protoOrdinal();
            case "ContractService.STORAGE" -> STATE_ID_CONTRACT_STORAGE.protoOrdinal();
            case "EntityIdService.ENTITY_ID" -> STATE_ID_ENTITY_ID.protoOrdinal();
            case "FeeService.MIDNIGHT_RATES" -> STATE_ID_MIDNIGHT_RATES.protoOrdinal();
            case "FileService.FILES" -> STATE_ID_FILES.protoOrdinal();
            case "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=150]]" -> STATE_ID_UPGRADE_DATA_150
                    .protoOrdinal();
            case "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=151]]" -> STATE_ID_UPGRADE_DATA_151
                    .protoOrdinal();
            case "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=152]]" -> STATE_ID_UPGRADE_DATA_152
                    .protoOrdinal();
            case "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=153]]" -> STATE_ID_UPGRADE_DATA_153
                    .protoOrdinal();
            case "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=154]]" -> STATE_ID_UPGRADE_DATA_154
                    .protoOrdinal();
            case "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=155]]" -> STATE_ID_UPGRADE_DATA_155
                    .protoOrdinal();
            case "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=156]]" -> STATE_ID_UPGRADE_DATA_156
                    .protoOrdinal();
            case "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=157]]" -> STATE_ID_UPGRADE_DATA_157
                    .protoOrdinal();
            case "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=158]]" -> STATE_ID_UPGRADE_DATA_158
                    .protoOrdinal();
            case "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=159]]" -> STATE_ID_UPGRADE_DATA_159
                    .protoOrdinal();
            case "FileService.UPGRADE_FILE" -> STATE_ID_UPGRADE_FILE.protoOrdinal();
            case "FreezeService.FREEZE_TIME" -> STATE_ID_FREEZE_TIME.protoOrdinal();
            case "FreezeService.UPGRADE_FILE_HASH" -> STATE_ID_UPGRADE_FILE_HASH.protoOrdinal();
            case "RecordCache.TransactionRecordQueue" -> STATE_ID_RECORD_QUEUE.protoOrdinal();
            case "ScheduleService.SCHEDULES_BY_EQUALITY" -> STATE_ID_SCHEDULES_BY_EQUALITY.protoOrdinal();
            case "ScheduleService.SCHEDULES_BY_EXPIRY_SEC" -> STATE_ID_SCHEDULES_BY_EXPIRY.protoOrdinal();
            case "ScheduleService.SCHEDULES_BY_ID" -> STATE_ID_SCHEDULES_BY_ID.protoOrdinal();
            case "TokenService.ACCOUNTS" -> STATE_ID_ACCOUNTS.protoOrdinal();
            case "TokenService.ALIASES" -> STATE_ID_ALIASES.protoOrdinal();
            case "TokenService.NFTS" -> STATE_ID_NFTS.protoOrdinal();
            case "TokenService.PENDING_AIRDROPS" -> STATE_ID_PENDING_AIRDROPS.protoOrdinal();
            case "TokenService.STAKING_INFOS" -> STATE_ID_STAKING_INFO.protoOrdinal();
            case "TokenService.STAKING_NETWORK_REWARDS" -> STATE_ID_NETWORK_REWARDS.protoOrdinal();
            case "TokenService.TOKEN_RELS" -> STATE_ID_TOKEN_RELATIONS.protoOrdinal();
            case "TokenService.TOKENS" -> STATE_ID_TOKENS.protoOrdinal();
            default -> throw new IllegalStateException("State has no identifier - '" + stateName + "'");
        };
    }

    /**
     * Given the inputs, compute the corresponding class ID.
     *
     * @param extra An extra string to bake into the class id
     * @return the class id
     */
    public static long computeClassId(
            @NonNull final String serviceName,
            @NonNull final String stateKey,
            @NonNull final SemanticVersion version,
            @NonNull final String extra) {
        // NOTE: Once this is live on any network, the formula used to generate this key can NEVER
        // BE CHANGED or you won't ever be able to deserialize an exising state! If we get away from
        // this formula, we will need to hardcode known classId that had been previously generated.
        final var ver = "v" + version.major() + "." + version.minor() + "." + version.patch();
        return hashString(serviceName + ":" + stateKey + ":" + ver + ":" + extra);
    }

    // Will be moved to `NonCryptographicHashing` with
    // https://github.com/swirlds/swirlds-platform/issues/6421
    static long hashString(@NonNull final String s) {
        // Normalize the string so things are deterministic (different JVMs might be using different
        // default internal representation for strings, and we need to normalize that)
        final var data = getNormalisedStringBytes(s);
        return hashBytes(data);
    }

    // Will be moved to `NonCryptographicHashing` with
    // https://github.com/swirlds/swirlds-platform/issues/6421
    private static long hashBytes(@NonNull final byte[] bytes) {
        // Go through the bytes. Conceptually, we get 8 bytes at a time, joined as a long,
        // and then xor it with our running hash. Then pass that into the hash64 function.
        // First, we divide the byte array into 8-byte blocks, and process them.
        final int numBlocks = bytes.length / 8;
        long hash = 0;
        for (int i = 0; i < (numBlocks * 8); i += 8) {
            hash ^= ((long) bytes[i] << 56);
            hash ^= ((long) bytes[i + 1] << 48);
            hash ^= ((long) bytes[i + 2] << 40);
            hash ^= ((long) bytes[i + 3] << 32);
            hash ^= (bytes[i + 4] << 24);
            hash ^= (bytes[i + 5] << 16);
            hash ^= (bytes[i + 6] << 8);
            hash ^= (bytes[i + 7]);
            hash = NonCryptographicHashing.hash64(hash);
        }

        // There may be 0 <= N <= 7 remaining bytes. Process these bytes
        final int numRemainingBytes = bytes.length - (numBlocks * 8);
        if (numRemainingBytes > 0) {
            for (int shift = numRemainingBytes * 8, i = (numBlocks * 8); i < bytes.length; i++, shift -= 8) {
                hash ^= ((long) bytes[i] << shift);
            }
            hash = NonCryptographicHashing.hash64(hash);
        }

        // We're done!
        return hash;
    }

    /**
     * Simple utility for checking whether the character is an underscore or dash on the ASCII
     * table.
     *
     * @param ch The character to check
     * @return True if the character is the underscore or dash character on the ascii table
     */
    private static boolean isAsciiUnderscoreOrDash(char ch) {
        return ch == '-' || ch == '_';
    }

    /**
     * Simple utility for checking whether the given character is A-Z or a-z on the ascii table.
     *
     * @param ch The character to check
     * @return True if the character is A-Z or a-z
     */
    private static boolean isAsciiLetter(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    /**
     * Simple utility to check whether the given character is a numeric digit between 0-9
     *
     * @param ch The character to check
     * @return True if the character is between ascii 0-9.
     */
    private static boolean isAsciiNumber(char ch) {
        return ch >= '0' && ch <= '9';
    }
}
