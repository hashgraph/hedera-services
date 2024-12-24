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

package com.hedera.node.app.hints;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A reconciliation loop to ensure the network is continually making progress toward
 * having the most up-to-date hinTS construction for the roster store.
 */
@Singleton
public class HintsReconciliationLoop {
    private final HintsConstructionControllers controllers;

    @Inject
    public HintsReconciliationLoop(@NonNull final HintsConstructionControllers controllers) {
        this.controllers = requireNonNull(controllers);
    }

    /**
     * Takes any actions needed to advance the state of the {@link HintsService} toward
     * having completed the most up-to-date hinTS construction for the roster store.
     * <p>
     * The basic flow examines the roster store to determine what combination of source/target
     * roster hashes would represent the most up-to-date hinTS construction. Given these source/target
     * hashes, it takes one of three courses of action:
     * <ol>
     *     <Li> If a completed construction already exists in {@link HintsService} state with the given
     *     source/target hashes, returns as a no-op.</Li>
     *     <Li> If there is already an active {@link HintsConstructionController} driving completion
     *     for the given source/target hashes, this call will invoke its
     *     {@link HintsConstructionController#advanceConstruction(Instant, WritableHintsStore)} method.</li>
     *     <Li>If there is no active {@link HintsConstructionController} for the given source/target
     *     hashes, this will create one based on the given consensus time and {@link HintsService} states;
     *     and will record the creation event in network state if this is the first time the network ever
     *     began reconciling a hinTS construction for these source/target hashes.</Li>
     * </ol>
     * <p>
     * <b>IMPORTANT:</b> Note that whether a new {@link HintsConstructionController} object is created, or an
     * appropriate one already exists, its subsequent behavior will be a deterministic function of the given
     * consensus time and {@link HintsService} states. That is, controllers are persistent objects <i>only</i>
     * due to performance considerations, but are <i>logically</i> driven deterministically by nothing but
     * network state and consensus time.
     *
     * @param now the current consensus time
     * @param rosterStore the roster store, for obtaining rosters if needed
     * @param hintsStore the hints store, for recording progress if needed
     */
    public void reconcileConstruction(
            @NonNull Instant now, @NonNull ReadableRosterStore rosterStore, @NonNull WritableHintsStore hintsStore) {
        final var currentRosterHash = requireNonNull(rosterStore.getCurrentRosterHash());
        final var candidateRosterHash = rosterStore.getCandidateRosterHash();
        final Bytes sourceRosterHash;
        final Bytes targetRosterHash;
        if (candidateRosterHash == null) {
            sourceRosterHash = rosterStore.getPreviousRosterHash();
            targetRosterHash = currentRosterHash;
        } else {
            sourceRosterHash = currentRosterHash;
            targetRosterHash = candidateRosterHash;
        }
        var construction = hintsStore.getConstructionFor(sourceRosterHash, targetRosterHash);
        if (construction == null) {
            construction = hintsStore.newConstructionFor(sourceRosterHash, targetRosterHash, rosterStore);
        }
        if (!construction.hasPreprocessedKeys()) {
            final var controller = controllers.getOrCreateControllerFor(construction, rosterStore);
            controller.advanceConstruction(now, hintsStore);
        }
    }
}
