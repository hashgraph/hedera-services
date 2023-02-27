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
package com.swirlds.platform.bls.crypto;

import java.util.ArrayList;
import java.util.List;

/** Contains the public keys that correspond to all keyshares of an epoch */
public class PublicKeyShares {
    /** The public keys of the epoch. The public key for a shareId is at index shareId - 1 */
    private final List<BlsPublicKey> publicKeys;

    /** Constructor */
    public PublicKeyShares() {
        this.publicKeys = new ArrayList<>();
    }

    /**
     * Copy constructor
     *
     * @param otherPublicKeyShares the {@link PublicKeyShares} object being copied
     */
    public PublicKeyShares(final PublicKeyShares otherPublicKeyShares) {
        final List<BlsPublicKey> newPublicKeys = new ArrayList<>();
        for (final BlsPublicKey publicKey : otherPublicKeyShares.publicKeys) {
            newPublicKeys.add(new BlsPublicKey(publicKey));
        }

        this.publicKeys = newPublicKeys;
    }

    /**
     * Gets the public key of a share id
     *
     * @param shareId the shareId to get the public key of
     * @return the public key of the share
     */
    public BlsPublicKey getPublicKey(final ShareId shareId) {
        // we must subtract 1, since shareId 1 is at index 0
        return publicKeys.get(shareId.id() - 1);
    }

    /**
     * Adds a public key. Keys must be added in order, corresponding to shareId
     *
     * @param publicKey the public key to add
     */
    public void addPublicKey(final BlsPublicKey publicKey) {
        publicKeys.add(publicKey);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null) {
            return false;
        }

        if (o instanceof PublicKeyShares otherKeyShares) {
            return publicKeys.equals(otherKeyShares.publicKeys);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return publicKeys.hashCode();
    }
}
