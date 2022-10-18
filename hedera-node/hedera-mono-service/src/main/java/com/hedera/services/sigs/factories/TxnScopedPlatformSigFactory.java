/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.factories;

import com.hedera.services.sigs.sourcing.KeyType;
import com.swirlds.common.crypto.TransactionSignature;

/**
 * Defines a type of {@link com.swirlds.common.crypto.TransactionSignature} factory that does not
 * require the {@code byte[]} data to sign, because it is assumed to be scoped to a gRPC
 * transaction.
 */
public interface TxnScopedPlatformSigFactory {
    /**
     * Returns an {@link com.swirlds.common.crypto.TransactionSignature} for the scoped transaction,
     * assuming the bytes signed are the bytes of the transaction in scope.
     *
     * @param publicKey the public key to use in creating the platform sig
     * @param sigBytes the cryptographic signature to use in creating the platform sig
     * @return a platform sig for the scoped transaction
     */
    TransactionSignature signBodyWithEd25519(byte[] publicKey, byte[] sigBytes);

    /**
     * Returns an {@link com.swirlds.common.crypto.TransactionSignature} for the scoped transaction,
     * assuming the bytes signed are the keccak256 hash of the bytes of the transaction in scope.
     *
     * @param publicKey the public key to use in creating the platform sig
     * @param sigBytes the cryptographic signature to use in creating the platform sig
     * @return a platform sig for the scoped transaction
     */
    TransactionSignature signKeccak256DigestWithSecp256k1(byte[] publicKey, byte[] sigBytes);

    /**
     * Convenience method to return a {@link com.swirlds.common.crypto.TransactionSignature} for the
     * scoped transaction with the given public key and signature and a specified type.
     *
     * @param type the type of the given public key
     * @param publicKey the public key to use in creating the verifiable signature
     * @param sigBytes the signature bytes to use in creating the verifiable signature
     * @return a platform sig for the scoped transaction
     */
    default TransactionSignature signAppropriately(
            KeyType type, byte[] publicKey, byte[] sigBytes) {
        switch (type) {
            default:
            case ED25519:
                return signBodyWithEd25519(publicKey, sigBytes);
            case ECDSA_SECP256K1:
                return signKeccak256DigestWithSecp256k1(publicKey, sigBytes);
        }
    }
}
