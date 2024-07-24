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

package com.hedera.cryptography.signaturescheme.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;

/**
 *  A Prototype implementation of PairingPrivateKey.
 *  This class will live in a different project once the implementation of the pairings-signature-library is completed.
 */
public record PairingPrivateKey() {

    /**
     * Creates a private key out of the CurveType and a random
     *
     * @param signatureSchema   The implementing curve type
     * @param random The environment secureRandom to use
     * @return a privateKey for that CurveType
     */
    @NonNull
    public static PairingPrivateKey create(
            @NonNull final SignatureSchema signatureSchema, @NonNull final Random random) {
        return new PairingPrivateKey();
    }

    /**
     * Create a public key from this private key.
     *
     * @return the public key
     */
    public PairingPublicKey createPublicKey() {
        return new PairingPublicKey();
    }
}
