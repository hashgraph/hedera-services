/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.stats.signing.algorithms;

import com.swirlds.common.crypto.SignatureType;

/**
 * Implements the {@code ECDSA} digital signature
 */
public class ECSecP256K1Algorithm extends ECSigningAlgorithm {

    /**
     * the unique algorithm identifier
     */
    private static final byte ALGORITHM_ID = 2;

    /**
     * the signature length in bytes
     */
    private static final int SIGNATURE_LENGTH = 64;

    /**
     * the public key length in bytes
     */
    private static final int PUBLIC_KEY_LENGTH = 64;

    /**
     * the elliptical curve coordinate size in bytes
     */
    private static final int EC_COORD_SIZE = 32;

    public ECSecP256K1Algorithm() {
        super(SignatureType.ECDSA_SECP256K1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getId() {
        return ALGORITHM_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSignatureLength() {
        return SIGNATURE_LENGTH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPublicKeyLength() {
        return PUBLIC_KEY_LENGTH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCoordinateSize() {
        return EC_COORD_SIZE;
    }
}
