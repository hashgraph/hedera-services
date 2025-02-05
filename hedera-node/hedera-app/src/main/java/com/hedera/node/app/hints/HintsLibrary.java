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

import com.hedera.node.app.hints.impl.HintsLibraryCodec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * The cryptographic operations required by the {@link HintsService}.
 * <p>
 * The relationship between the hinTS algorithms and these operations are as follows:
 * <ul>
 *   <li><b>CRS creation</b> ({@code Setup}) - Implemented by using {@link HintsLibrary#newCrs(int)},
 *   {@link HintsLibrary#updateCrs(Bytes, Bytes)}, and {@link HintsLibrary#verifyCrsUpdate(Bytes, Bytes, Bytes)}.</li>
 *   <li><b>Key generation</b> ({@code KGen}) - Implemented by {@link HintsLibrary#newBlsKeyPair()}.</li>
 *   <li><b>Hint generation</b> ({@code HintGen}) - Implemented by {@link HintsLibrary#computeHints(Bytes, int, int)}.</li>
 *   <li><b>Preprocessing</b> ({@code Preprocess}) - Implemented by using {@link HintsLibrary#preprocess(Map, Map, int)}
 *   to select the hinTS keys to use as input to {@link HintsLibrary#preprocess(Map, Map, int)}.</li>
 *   <li><b>Partial signatures</b> ({@code Sign}) - Implemented by {@link HintsLibrary#signBls(Bytes, Bytes)}.</li>
 *   <li><b>Verifying partial signatures</b> ({@code PartialVerify}) - Implemented by using
 *   {@link HintsLibrary#verifyBls(Bytes, Bytes, Bytes)} with public keys extracted from the
 *   aggregation key in the active hinTS scheme via {@link HintsLibraryCodec#extractPublicKey(Bytes, int)}.</li>
 *   <li><b>Signature aggregation</b> ({@code SignAggr}) - Implemented by {@link HintsLibrary#aggregateSignatures(Bytes, Bytes, Map)}
 *   with partial signatures verified as above with weights extracted from the aggregation key in the active hinTS
 *   scheme via {@link HintsLibraryCodec#extractWeight(Bytes, int)} and {@link HintsLibraryCodec#extractTotalWeight(Bytes)}.</li>
 *   <li><b>Verifying aggregate signatures</b> ({@code Verify}) - Implemented by
 *   {@link HintsLibrary#verifyAggregate(Bytes, Bytes, Bytes, long, long)}.</li>
 * </ul>
 */
public interface HintsLibrary {
    /**
     * Returns an initial CRS for the given number of parties.
     * @param n the number of parties
     * @return the CRS
     */
    Bytes newCrs(int n);

    /**
     * Updates the given CRS with the given 128 bits of entropy and returns the concatenation of the
     * updated CRS and a proof of the contribution.
     * @param crs the CRS
     * @param entropy the 128-bit entropy
     * @return the updated CRS and proof
     */
    Bytes updateCrs(@NonNull Bytes crs, @NonNull Bytes entropy);

    /**
     * Verifies the given proof of a CRS update.
     * @param oldCrs the old CRS
     * @param newCrs the new CRS
     * @param proof the proof
     * @return true if the proof is valid; false otherwise
     */
    boolean verifyCrsUpdate(@NonNull Bytes oldCrs, @NonNull Bytes newCrs, @NonNull Bytes proof);

    /**
     * Generates a new BLS key pair.
     * @return the key pair
     */
    Bytes newBlsKeyPair();

    /**
     * Computes the hints for the given public key and number of parties.
     *
     * @param blsPrivateKey the private key
     * @param partyId the party id
     * @param n the number of parties
     * @return the hints
     */
    Bytes computeHints(@NonNull Bytes blsPrivateKey, int partyId, int n);

    /**
     * Validates the hinTS public key for the given number of parties.
     *
     * @param hintsKey the hinTS key
     * @param partyId the party id
     * @param n the number of parties
     * @return true if the hints are valid; false otherwise
     */
    boolean validateHintsKey(@NonNull Bytes hintsKey, int partyId, int n);

    /**
     * Runs the hinTS preprocessing algorithm on the given validated hint keys and party weights for the given number
     * of parties. The output includes,
     * <ol>
     *     <li>The linear size aggregation key to use in combining partial signatures on a message with a provably
     *     well-formed aggregate public key.</li>
     *     <li>The succinct verification key to use when verifying an aggregate signature.</li>
     * </ol>
     * Both maps given must have the same key set; in particular, a subset of {@code [0, n)}.
     * @param hintsKeys the valid hinTS keys by party id
     * @param weights the weights by party id
     * @param n the number of parties
     * @return the preprocessed keys
     */
    Bytes preprocess(@NonNull Map<Integer, Bytes> hintsKeys, @NonNull Map<Integer, Long> weights, int n);

    /**
     * Signs a message with a BLS private key.
     *
     * @param message the message
     * @param privateKey the private key
     * @return the signature
     */
    Bytes signBls(@NonNull Bytes message, @NonNull Bytes privateKey);

    /**
     * Checks that a signature on a message verifies under a BLS public key.
     *
     * @param signature the signature
     * @param message the message
     * @param publicKey the public key
     * @return true if the signature is valid; false otherwise
     */
    boolean verifyBls(@NonNull Bytes signature, @NonNull Bytes message, @NonNull Bytes publicKey);

    /**
     * Aggregates the signatures for party ids using hinTS aggregation and verification keys.
     *
     * @param aggregationKey the aggregation key
     * @param verificationKey the verification key
     * @param partialSignatures the partial signatures by party id
     * @return the aggregated signature
     */
    Bytes aggregateSignatures(
            @NonNull Bytes aggregationKey,
            @NonNull Bytes verificationKey,
            @NonNull Map<Integer, Bytes> partialSignatures);

    /**
     * Checks an aggregate signature on a message verifies under a hinTS verification key, where
     * this is only true if the aggregate signature has weight exceeding the specified threshold
     * or total weight stipulated in the verification key.
     *
     * @param signature the aggregate signature
     * @param message the message
     * @param verificationKey the verification key
     * @param thresholdNumerator the numerator of a fraction of total weight the signature must have
     * @param thresholdDenominator the denominator of a fraction of total weight the signature must have
     * @return true if the signature is valid; false otherwise
     */
    boolean verifyAggregate(
            @NonNull Bytes signature,
            @NonNull Bytes message,
            @NonNull Bytes verificationKey,
            long thresholdNumerator,
            long thresholdDenominator);
}
