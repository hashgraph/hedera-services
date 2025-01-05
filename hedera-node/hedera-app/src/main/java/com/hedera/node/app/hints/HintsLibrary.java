/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hints;

import com.hedera.cryptography.bls.BlsKeyPair;
import com.hedera.cryptography.bls.BlsPrivateKey;
import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.cryptography.bls.BlsSignature;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Map;

/**
 * The cryptographic operations required by the {@link HintsService}.
 */
public interface HintsLibrary {
    /**
     * Generates a new BLS key pair.
     * @return the key pair
     */
    BlsKeyPair newBlsKeyPair();

    /**
     * Signs the given message with the given private key.
     * @param message the message
     * @param key the private key
     * @return the signature
     */
    BlsSignature signPartial(@NonNull Bytes message, @NonNull BlsPrivateKey key);

    /**
     * Verifies the given signature for the given message and public key.
     * @param message the message
     * @param signature the signature
     * @param publicKey the public key
     * @return true if the signature is valid; false otherwise
     */
    boolean verifyPartial(@NonNull Bytes message, @NonNull BlsSignature signature, @NonNull BlsPublicKey publicKey);

    /**
     * Aggregates the signatures for the given party ids with the given aggregation key.
     * @param aggregationKey the aggregation key
     * @return the aggregated signature
     */
    Bytes aggregateSignatures(@NonNull Bytes aggregationKey, @NonNull Map<Long, BlsSignature> signatures);

    /**
     * Extracts the public key for the given party id from the given aggregation key.
     * @param aggregationKey the aggregation key
     * @param partyId the party id
     * @return the public key
     */
    BlsPublicKey extractPublicKey(@NonNull Bytes aggregationKey, long partyId);

    /**
     * Extracts the weight of the given party id from the given aggregation key.
     * @param aggregationKey the aggregation key
     * @param partyId the party id
     * @return the weight
     */
    long extractWeight(@NonNull Bytes aggregationKey, long partyId);

    /**
     * Extracts the total weight of all parties from the given aggregation key.
     * @param aggregationKey the aggregation key
     * @return the weight
     */
    long extractTotalWeight(@NonNull Bytes aggregationKey);

    /**
     * Computes the hints for the given public key and number of parties.
     * @param privateKey the private key
     * @param n the number of parties
     * @return the hints
     */
    Bytes computeHints(@NonNull BlsPrivateKey privateKey, int n);

    /**
     * Validates the hinTS public key for the given number of parties.
     *
     * @param n the number of parties
     * @return true if the hints are valid; false otherwise
     */
    boolean validate(@NonNull Bytes hintsKey, int n);

    /**
     * Aggregates the given validated hint keys and party weights into preprocessed keys.
     * @param hintKeys the valid hint keys by party id
     * @param weights the weights by party id
     * @param n the number of parties
     * @return the aggregated keys
     */
    Bytes preprocess(@NonNull Map<Long, Bytes> hintKeys, @NonNull Map<Long, Long> weights, int n);
}
