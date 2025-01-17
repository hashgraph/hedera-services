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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.node.app.history.HistoryLibrary;
import com.hedera.node.app.history.ReadableHistoryStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProofControllers {
    private static final long NO_CONSTRUCTION_ID = -1L;

    private final Executor executor;
    private final ProofKeysAccessor keyAccessor;
    private final HistoryLibrary library;
    private final HistoryLibraryCodec codec;
    private final HistorySubmissions submissions;
    private final Supplier<NodeInfo> selfNodeInfoSupplier;
    private final Consumer<HistoryProof> proofConsumer;

    /**
     * May be null if the node has just started, or if the network has completed the most up-to-date
     * construction implied by its roster store.
     */
    @Nullable
    private ProofController controller;

    @Inject
    public ProofControllers(
            @NonNull final Executor executor,
            @NonNull final ProofKeysAccessor keyAccessor,
            @NonNull final HistoryLibrary library,
            @NonNull final HistoryLibraryCodec codec,
            @NonNull final HistorySubmissions submissions,
            @NonNull final Supplier<NodeInfo> selfNodeInfoSupplier,
            @NonNull final Consumer<HistoryProof> proofConsumer) {
        this.executor = requireNonNull(executor);
        this.keyAccessor = requireNonNull(keyAccessor);
        this.library = requireNonNull(library);
        this.codec = requireNonNull(codec);
        this.submissions = requireNonNull(submissions);
        this.selfNodeInfoSupplier = requireNonNull(selfNodeInfoSupplier);
        this.proofConsumer = requireNonNull(proofConsumer);
    }

    /**
     * Creates a new controller for the given history proof construction, sourcing its rosters from the given store.
     *
     * @param activeRosters the active rosters
     * @param construction the construction
     * @param historyStore the history store
     * @return the result of the operation
     */
    public @NonNull ProofController getOrCreateFor(
            @NonNull final ActiveRosters activeRosters,
            @NonNull final HistoryProofConstruction construction,
            @NonNull final ReadableHistoryStore historyStore) {
        requireNonNull(activeRosters);
        requireNonNull(construction);
        requireNonNull(historyStore);
        if (currentConstructionId() != construction.constructionId()) {
            if (controller != null) {
                controller.cancelPendingWork();
            }
            controller = newControllerFor(activeRosters, construction, historyStore);
        }
        return requireNonNull(controller);
    }

    /**
     * Returns a new controller for the given active rosters and history proof construction.
     * @param activeRosters the active rosters
     * @param construction the proof construction
     * @param historyStore the history store
     * @return the controller
     */
    private ProofController newControllerFor(
            @NonNull final ActiveRosters activeRosters,
            @NonNull final HistoryProofConstruction construction,
            @NonNull final ReadableHistoryStore historyStore) {
        final var weights = activeRosters.transitionWeights();
        if (!weights.sourceNodesHaveTargetThreshold()) {
            return new InertProofController(construction.constructionId());
        } else {
            final var selfId = selfNodeInfoSupplier.get().nodeId();
            final var schnorrKeyPair = keyAccessor.getOrCreateSchnorrKeyPair(construction.constructionId());
            final var keyPublications = historyStore.getProofKeyPublications(weights.targetNodeIds());
            final var signaturePublications =
                    historyStore.getSignaturePublications(construction.constructionId(), weights.targetNodeIds());
            return new ProofControllerImpl(
                    selfId,
                    schnorrKeyPair,
                    historyStore.getLedgerId(),
                    construction,
                    weights,
                    executor,
                    library,
                    codec,
                    submissions,
                    keyPublications,
                    signaturePublications,
                    proofConsumer);
        }
    }

    /**
     * Returns the ID of the current proof construction, or {@link #NO_CONSTRUCTION_ID} if there is none.
     */
    private long currentConstructionId() {
        return controller != null ? controller.constructionId() : NO_CONSTRUCTION_ID;
    }
}
