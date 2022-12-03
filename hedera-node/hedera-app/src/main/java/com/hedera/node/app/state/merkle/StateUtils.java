/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

class StateUtils {
    private static final MessageDigest SHA1_DIGEST;

    static {
        try {
            SHA1_DIGEST = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Verifies the state key meets all the validation requirements.
     *
     * @param stateKey The state key
     * @return The state key provided as the argument
     * @throws NullPointerException if the state key is null
     * @throws IllegalArgumentException if any other validation criteria fails
     */
    static String validateStateKey(@NonNull final String stateKey) {
        if (Objects.requireNonNull(stateKey).isEmpty()) {
            throw new IllegalArgumentException("The state key must have characters");
        }

        for (int i = 0; i < stateKey.length(); i++) {
            final var c = stateKey.charAt(i);
            if (!Character.isSpaceChar(c) && !Character.isAlphabetic(c) && !Character.isDigit(c)) {
                throw new IllegalArgumentException("Illegal character at position " + i);
            }
        }

        return stateKey;
    }

    /**
     * Given the inputs, compute the corresponding class ID.
     *
     * @param serviceName The service name
     * @param stateKey The state key
     * @return the class id
     */
    static long computeClassId(@NonNull final String serviceName, @NonNull final String stateKey) {
        // NOTE: Once this is live on any network, the formula used to generate this key can NEVER
        // BE CHANGED
        // or you won't ever be able to deserialize an exising state!
        final var uniqueKey = serviceName + ":" + stateKey;
        final var hash = SHA1_DIGEST.digest(uniqueKey.getBytes(StandardCharsets.UTF_8));
        long uniqueId = 0L; // maybe take a long and add all bytes and let it overflow.
        for (byte b : hash) {
            uniqueId += b;
        }

        return uniqueId;
    }
}
