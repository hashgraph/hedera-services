/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.cryptography.bls;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;

/**
 * Represents a BLS key pair.
 *
 * @param privateKey the private key
 * @param publicKey  the public key
 */
public record BlsKeyPair(@NonNull BlsPrivateKey privateKey, @NonNull BlsPublicKey publicKey) {
    /**
     * Constructs a new PairingKeyPair
     *
     * @param privateKey the private key
     * @param publicKey  the public key
     */
    public BlsKeyPair {
        Objects.requireNonNull(privateKey, "privateKey cannot be null");
        Objects.requireNonNull(publicKey, "publicKey cannot be null");
        if (privateKey.signatureSchema() != publicKey.signatureSchema()) {
            throw new IllegalArgumentException("The private key and public key must have the same signature schema");
        }
    }

    /**
     * Generates a Key Pair (private and public keys)
     *
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @return a key pair
     * @throws NoSuchAlgorithmException if no algorithm found to get a {@link SecureRandom} instance
     */
    @NonNull
    public static BlsKeyPair generate(@NonNull final SignatureSchema signatureSchema) throws NoSuchAlgorithmException {
        return generate(signatureSchema, SecureRandom.getInstanceStrong());
    }

    /**
     * Generates a Key Pair (private and public keys)
     *
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @param random a source of randomness
     * @return a key pair
     */
    @NonNull
    public static BlsKeyPair generate(@NonNull final SignatureSchema signatureSchema, @NonNull final Random random) {
        final BlsPrivateKey blsPrivateKey =
                BlsPrivateKey.create(Objects.requireNonNull(signatureSchema, "signatureSchema cannot be null"), random);
        return new BlsKeyPair(blsPrivateKey, blsPrivateKey.createPublicKey());
    }
}
