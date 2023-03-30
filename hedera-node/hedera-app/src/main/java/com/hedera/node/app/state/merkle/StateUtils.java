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

package com.hedera.node.app.state.merkle;

import static com.swirlds.common.utility.CommonUtils.getNormalisedStringBytes;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.utility.NonCryptographicHashing;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Utility class for working with states. */
public final class StateUtils {
    /** Prevent instantiation */
    private StateUtils() {}

    /**
     * Verifies the service name meets all the validation requirements.
     *
     * @param serviceName The service name
     * @return The service name provided as the argument
     * @throws NullPointerException if the service name is null
     * @throws IllegalArgumentException if any other validation criteria fails
     */
    @NonNull
    static String validateServiceName(@NonNull final String serviceName) {
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
    static String validateStateKey(@NonNull final String stateKey) {
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
     * Given the inputs, compute the corresponding class ID.
     *
     * @param extra An extra string to bake into the class id
     * @return the class id
     */
    static long computeClassId(@NonNull final StateMetadata<?, ?> md, @NonNull final String extra) {
        final var def = md.stateDefinition();
        return computeClassId(md.serviceName(), def.stateKey(), md.schema().getVersion(), extra);
    }

    /**
     * Given the inputs, compute the corresponding class ID.
     *
     * @param extra An extra string to bake into the class id
     * @return the class id
     */
    static long computeClassId(
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

    /**
     * Given a {@link Codec} and a {@link SerializableDataInputStream}, reads an array length and
     * then a {@code byte[]} array of that length from the stream; and then returns the result of
     * parsing that {@code byte[]} into an object with the codec.
     *
     * @param codec a {@link Codec} to use to parse the {@code byte[]} array
     * @param in a {@link SerializableDataInputStream} to read the array length and array from
     * @return the result of parsing the {@code byte[]} array into an object with the codec
     * @param <T> the type of object used by the codec
     * @throws IOException if there is an error reading from the stream
     */
    public static <T> T deserializeViaBytes(
            @NonNull final Codec<T> codec, @NonNull final SerializableDataInputStream in) throws IOException {
        final var length = in.readInt();
        final var paddedBytes = new byte[length + 1];
        in.readNBytes(paddedBytes, 0, length);
        return codec.parse(BufferedData.wrap(paddedBytes));
    }

    /**
     * Given a {@link Codec} and a {@link SerializableDataOutputStream}, writes the given object
     * to a {@code byte[]} array using the codec; and then writes the length of that array and the
     * array itself to the stream.
     *
     * @param data the object to serialize with the codec
     * @param codec a {@link Codec} to use to serialize the object
     * @param out a {@link SerializableDataOutputStream} to write the array length and array to
     * @param <T> the type of object used by the codec
     * @throws IOException if there is an error writing to the stream
     */
    public static <T> void serializeViaBytes(
            @NonNull final T data, @NonNull final Codec<T> codec, @NonNull final SerializableDataOutputStream out)
            throws IOException {
        final var baos = new ByteArrayOutputStream();
        codec.write(data, new WritableStreamingData(baos));
        baos.flush();
        final var bytes = baos.toByteArray();
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
