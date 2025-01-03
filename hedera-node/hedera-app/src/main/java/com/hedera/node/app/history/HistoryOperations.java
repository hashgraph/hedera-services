/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.history;

import com.hedera.hapi.node.state.history.ProofRoster;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * The cryptographic operations required by the {@link HistoryService}.
 */
public interface HistoryOperations {
    /**
     * Signs the given message with the given Schnorr private key.
     * @param message the message
     * @param privateKey the Schnorr private key
     * @return the signature
     */
    Bytes signSchnorr(@NonNull Bytes message, @NonNull Bytes privateKey);

    /**
     * Validates the Schnorr signature for the given message and public key.
     * @param publicKey the public key
     * @param message the message
     * @return true if the hints are valid; false otherwise
     */
    boolean verifySchnorr(@NonNull Bytes publicKey, @NonNull Bytes message);

    /**
     * Hashes the given proof roster.
     * @param roster the proof roster
     * @return the hash of the proof roster
     */
    Bytes hashProofRoster(@NonNull ProofRoster roster);

    /**
     * Returns a SNARK recursively proving the derivation of the target roster and metadata from the
     * ledger id (unless the source roster hash <i>is</i> the ledger id, which is the base case of
     * the recursion).
     * @param ledgerId the ledger id
     * @param sourceProof if not null, the proof the source roster was derived from the ledger id
     * @param sourceProofRoster the source roster
     * @param targetProofRosterHash the hash of the target roster
     * @param targetMetadata the metadata of the target roster
     * @param sourceSignatures the signatures by node id in the source roster on the target roster hash and its metadata
     * @return the SNARK proving the derivation of the target roster and metadata from the ledger id
     */
    @NonNull
    Bytes proveTransition(
            @NonNull Bytes ledgerId,
            @Nullable Bytes sourceProof,
            @NonNull ProofRoster sourceProofRoster,
            @NonNull Bytes targetProofRosterHash,
            @NonNull Bytes targetMetadata,
            @NonNull Map<Long, Bytes> sourceSignatures);

    /**
     * Verifies the given SNARK proves a set of roster transitions from the ledger id to the target roster and metadata.
     * @param ledgerId the ledger id
     * @param targetProofRosterHash the hash of the target roster
     * @param targetMetadata the metadata of the target roster
     * @param proof the SNARK
     * @return true if the proof is valid; false otherwise
     */
    boolean verifyTransitionProof(
            @NonNull Bytes ledgerId,
            @NonNull Bytes targetProofRosterHash,
            @NonNull Bytes targetMetadata,
            @NonNull Bytes proof);
}
