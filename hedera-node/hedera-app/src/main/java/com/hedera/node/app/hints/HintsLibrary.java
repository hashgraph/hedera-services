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
import com.hedera.cryptography.bls.BlsSignature;
import com.hedera.hapi.node.state.hints.HintsKey;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * The cryptographic operations required by the {@link HintsService}. The relationship between the hinTS algorithms
 * and these operations are as follows:
 * <ul>
 *   <li><b>Key generation</b> ({@code KGen}) - Implemented by {@link HintsLibrary#newBlsKeyPair()}.</li>
 *   <li><b>Hint generation</b> ({@code HintGen}) - Implemented by {@link HintsLibrary#computeHints(BlsPrivateKey, int, int)}.</li>
 *   <li><b>Preprocessing</b> ({@code Preprocess}) - Implemented by using {@link HintsLibrary#validateHintsKey(HintsKey, int)}
 *   to select the hinTS keys to use as input to {@link HintsLibrary#preprocess(Map, Map, int)}.</li>
 *   <li><b>Partial signatures</b> ({@code Sign}) - Implemented by {@link HintsLibrary#signPartial(Bytes, BlsPrivateKey)}.</li>
 *   <li><b>Verifying partial signatures</b> ({@code PartialVerify}) - Implemented by using
 *   {@link HintsLibrary#verifyPartial(Bytes, BlsSignature, Bytes)} with public keys extracted from the
 *   aggregation key in the active hinTS scheme via {@link HintsLibrary#extractPublicKey(Bytes, long)}.</li>
 *   <li><b>Signature aggregation</b> ({@code SignAggr}) - Implemented by {@link HintsLibrary#signAggregate(Bytes, Map)}
 *   with partial signatures verified as above with weights extracted from the aggregation key in the active hinTS
 *   scheme via {@link HintsLibrary#extractWeight(Bytes, long)} and {@link HintsLibrary#extractTotalWeight(Bytes)}.</li>
 *   <li><b>Verifying aggregate signatures</b> ({@code Verify}) - Implemented by
 *   {@link HintsLibrary#verifyAggregate(Bytes, Bytes, long, Bytes)}.</li>
 * </ul>
 */
public interface HintsLibrary {
    /**
     * Generates a new BLS key pair.
     * @return the key pair
     */
    BlsKeyPair newBlsKeyPair();

    /**
     * Computes the hints for the given public key and number of parties.
     *
     * @param privateKey the private key
     * @param partyId the party id
     * @param n the number of parties
     * @return the hints
     */
    Bytes computeHints(@NonNull BlsPrivateKey privateKey, int partyId, int n);

    /**
     * Validates the hinTS public key for the given number of parties.
     *
     * @param n the number of parties
     * @return true if the hints are valid; false otherwise
     */
    boolean validateHintsKey(@NonNull HintsKey hintsKey, int n);

    /**
     * Runs the hinTS preprocessing algorithm on the given validated hint keys and party weights for the given number
     * of parties. The output includes,
     * <ol>
     *     <li>The linear size aggregation key to use in combining partial signatures on a message with a provably
     *     well-formed aggregate public key.</li>
     *     <li>The succinct verification key to use when verifying an aggregate signature.</li>
     * </ol>
     * @param hintKeys the valid hinTS keys by party id
     * @param weights the weights by party id
     * @param n the number of parties
     * @return the preprocessed keys
     */
    PreprocessedKeys preprocess(@NonNull Map<Integer, HintsKey> hintKeys, @NonNull Map<Integer, Long> weights, int n);

    /**
     * Signs the given message with the given private key.
     * @param message the message
     * @param key the private key
     * @return the signature
     */
    BlsSignature signPartial(@NonNull Bytes message, @NonNull BlsPrivateKey key);

    /**
     * Extracts the public key for the given party id from the given aggregation key.
     *
     * @param aggregationKey the aggregation key
     * @param partyId the party id
     * @return the public key
     */
    Bytes extractPublicKey(@NonNull Bytes aggregationKey, long partyId);

    /**
     * Verifies the given signature for the given message and public key.
     * @param message the message
     * @param signature the signature
     * @param publicKey the public key
     * @return true if the signature is valid; false otherwise
     */
    boolean verifyPartial(@NonNull Bytes message, @NonNull BlsSignature signature, @NonNull Bytes publicKey);

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
     * Aggregates the signatures for the given party ids with the given aggregation key.
     * @param aggregationKey the aggregation key
     * @return the aggregated signature
     */
    Bytes signAggregate(@NonNull Bytes aggregationKey, @NonNull Map<Long, BlsSignature> partialSignatures);

    /**
     * Verifies the aggregate signature and its claimed threshold weight for the given message and verification key.
     * @param message the message
     * @param signature the aggregate signature
     * @param threshold the threshold weight
     * @param verificationKey the verification key
     * @return the aggregated signature
     */
    boolean verifyAggregate(
            @NonNull Bytes message, @NonNull Bytes signature, long threshold, @NonNull Bytes verificationKey);
}
