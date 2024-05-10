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

package com.swirlds.platform.tss.bls;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * Cryptographic operations for BLS TSS signatures.
 */
public interface BlsCryptography {

    /**
     * Sign a message using a BLS private key.
     *
     * @param privateKey the private key to sign with
     * @param message    the message to sign
     * @return the signature
     */
    @NonNull
    BlsSignature sign(@NonNull BlsPrivateKey privateKey, @NonNull byte[] message);

    /**
     * Verify a signed message with the known public key.
     *
     * @param publicKey the public key to verify with
     * @param signature the signature to verify
     * @param message   the message that was signed
     * @return true if the signature is valid, false otherwise
     */
    boolean verifySignature(
            @NonNull BlsPublicKey publicKey, @NonNull BlsSignature signature, @NonNull final byte[] message);


    /**
     * Verify a aggregate signed message with the known aggregated public key.
     *
     * @param publicKey the public key to verify with
     * @param signature the signature to verify
     * @param message   the message that was signed
     * @return true if the signature is valid, false otherwise
     */
    boolean verifySignature(
            @NonNull BlsAggregatePublicKey publicKey, @NonNull BlsAggregateSignature signature, @NonNull final byte[] message);

    /**
     * Aggregate a list of BLS signatures into a single signature.
     *
     * @param signatures the signatures to aggregate
     * @return the aggregate signature
     */
    @NonNull
    BlsAggregateSignature aggregateSignature(@NonNull final Map<Integer, BlsSignature> signatures);

    /**
     * Verify an aggregate BLS signature.
     *
     * @param signature the signature to verify
     * @param publicKey the public key to verify with
     * @param message   the message that was signed
     * @return true if the signature is valid, false otherwise
     */
    boolean verifyAggregateSignature(
            @NonNull final BlsAggregateSignature signature,
            @NonNull final BlsAggregatePublicKey publicKey,
            @NonNull final byte[] message);
}
