/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.signature;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Helper class that contains all functionality for verifying signatures during handle.
 */
public interface KeyVerifier {

    /**
     * Get a {@link SignatureVerification} for the given key.
     *
     * <p>If the key is a cryptographic key (i.e. a basic key like ED25519 or ECDSA_SECP256K1), and the cryptographic
     * key was in the signature map of the transaction, then a {@link SignatureVerification} will be for that key.
     * If there was no such cryptographic key in the signature map, {@code null} is returned.
     *
     * <p>If the key is a key list, then a {@link SignatureVerification} will be returned that aggregates the results
     * of each key in the key list, possibly nested.
     *
     * <p>If the key is a threshold key, then a {@link SignatureVerification} will be returned that aggregates the
     * results of each key in the threshold key, possibly nested, based on the threshold for that key.
     *
     * @param key The key to check on the verification results for.
     * @return A {@link SignatureVerification} for the given key, if available, {@code null} otherwise.
     */
    @NonNull
    SignatureVerification verificationFor(@NonNull Key key);

    /**
     * Gets the {@link SignatureVerification} for the given key. If this key was not provided during pre-handle, then
     * there will be no corresponding {@link SignatureVerification}. If the key was provided during pre-handle, then the
     * corresponding {@link SignatureVerification} will be returned with the result of that verification operation.
     * Additionally, the VerificationAssistant provided may modify the result for "primitive", "Contract ID", or
     * "Delegatable Contract ID" keys, and will be called to observe and reply for each such key as it is processed.
     *
     * @param key the key to get the verification for
     * @param callback a VerificationAssistant callback function that will observe each "primitive", "Contract ID", or
     * "Delegatable Contract ID" key and return a boolean indicating if the given key should be considered valid.
     * @return the verification for the given key, or {@code null} if no such key was provided during pre-handle
     */
    @NonNull
    SignatureVerification verificationFor(@NonNull Key key, @NonNull VerificationAssistant callback);

    /**
     * Look for a {@link SignatureVerification} that applies to the given hollow account.
     * @param evmAlias The evm alias to lookup verification for.
     * @return The {@link SignatureVerification} for the given hollow account.
     */
    @NonNull
    SignatureVerification verificationFor(@NonNull Bytes evmAlias);

    /**
     * Gets the number of signatures verified for this transaction.
     *
     * @return the number of signatures verified for this transaction. Non-negative.
     */
    int numSignaturesVerified();
}
