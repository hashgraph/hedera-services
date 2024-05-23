/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.pairings.bls12381.impl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Contains static utility functions */
public final class Utils {
    private Utils() {}

    /**
     * Computes SHA 256 hash
     *
     * @param message message to hash
     * @return 256-bit hash
     */
    public static byte[] computeSha256(final byte[] message) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(message);

            return digest.digest();
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
