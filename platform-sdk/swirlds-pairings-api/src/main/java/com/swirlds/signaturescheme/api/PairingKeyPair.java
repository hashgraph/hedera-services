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

package com.swirlds.signaturescheme.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;

/**
 * Represents a key pair consisting of a public key and a private key.
 */
public record PairingKeyPair(@NonNull PairingPublicKey publicKey, @NonNull PairingPrivateKey privateKey) {
    /**
     * Create a key pair for a given schema, from a source of randomness.
     *
     * @param schema the signature schema to create the key pair for
     * @param random the source of randomness
     * @return the key pair
     */
    public static PairingKeyPair create(@NonNull final SignatureSchema schema, @NonNull final Random random) {
        final PairingPrivateKey privateKey = PairingPrivateKey.create(schema, random);
        final PairingPublicKey publicKey = PairingPublicKey.create(privateKey);

        return new PairingKeyPair(publicKey, privateKey);
    }
}
