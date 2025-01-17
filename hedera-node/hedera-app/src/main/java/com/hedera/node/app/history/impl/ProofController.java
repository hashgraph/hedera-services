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

package com.hedera.node.app.history.impl;

import com.hedera.hapi.node.state.history.HistoryProofVote;
import com.hedera.node.app.history.ReadableHistoryStore.HistorySignaturePublication;
import com.hedera.node.app.history.ReadableHistoryStore.ProofKeyPublication;
import com.hedera.node.app.history.WritableHistoryStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

public interface ProofController {
    /**
     * Acts relative to the given state to let this node help advance the ongoing metadata proof
     * construction toward a deterministic completion.
     *
     * @param now the current consensus time
     * @param metadata the latest known metadata to be proven
     * @param historyStore the history store, in case the controller is able to complete the construction
     */
    void advanceConstruction(
            @NonNull Instant now, @Nullable Bytes metadata, @NonNull WritableHistoryStore historyStore);

    /**
     * Incorporates the proof key published by the given node, if this construction has not already "locked in"
     * its assembled {@code (address book hash, metadata)} history.
     *
     * @param publication the proof key publication
     */
    void addProofKeyPublication(@NonNull ProofKeyPublication publication);

    /**
     * If this controller's construction is not already complete, considers updating its state with this history
     * proof vote. Returns true if any state was updated.
     * <p>
     * <b>Important:</b> If this vote results in an output having at least 1/3 of consensus weight, also updates
     * <i>network</i> state in the given writable store with the winning proof.
     * @param nodeId the node ID
     * @param vote the history proof vote
     * @param historyStore the history store
     */
    boolean addProofVote(long nodeId, @NonNull HistoryProofVote vote, @NonNull WritableHistoryStore historyStore);

    /**
     * Incorporates the given history signature, if this construction still needs a proof, and does
     * not already have a signature published by the associated node.
     *
     * @param publication the proof key publication
     */
    void addSignaturePublication(@NonNull HistorySignaturePublication publication);

    /**
     * Cancels any pending work that this controller has scheduled.
     */
    void cancelPendingWork();
}
