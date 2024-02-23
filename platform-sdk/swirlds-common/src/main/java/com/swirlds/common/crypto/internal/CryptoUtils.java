/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

public abstract class CryptoUtils {
    /** the type of hash to use */
    private static final String HASH_TYPE = "SHA-384";

    private static final String PRNG_TYPE = "SHA1PRNG";
    private static final String PRNG_PROVIDER = "SUN";

    // return the MessageDigest for the type of hash function used throughout the code
    public static MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance(HASH_TYPE);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create an instance of the default deterministic {@link SecureRandom}
     *
     * @return an instance of {@link SecureRandom}
     * @throws NoSuchProviderException
     * 		if the security provider is not available on the system
     * @throws NoSuchAlgorithmException
     * 		if the algorithm is not available on the system
     */
    public static SecureRandom getDetRandom() throws NoSuchProviderException, NoSuchAlgorithmException {
        return SecureRandom.getInstance(PRNG_TYPE, PRNG_PROVIDER);
    }
}
