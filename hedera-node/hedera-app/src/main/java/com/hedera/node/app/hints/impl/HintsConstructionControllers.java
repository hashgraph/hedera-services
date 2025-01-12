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

package com.hedera.node.app.hints.impl;

import static com.hedera.node.app.hints.HintsService.partySizeForRosterNodeCount;
import static com.hedera.node.app.roster.ActiveRosters.Phase.BOOTSTRAP;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.hints.HintsKeyAccessor;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.node.app.hints.HintsService;
import com.hedera.node.app.hints.ReadableHintsStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.config.data.TssConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Logic to get or create a {@link HintsController} for source/target roster hashes
 * relative to the current {@link HintsService} state and consensus time. We only support one
 * controller at a time, so if this class detects that a new controller is needed, it will cancel
 * any existing controller via {@link HintsController#cancelPendingWork()} and release its
 * reference to the cancelled controller.
 */
@Singleton
public class HintsConstructionControllers {
    private static final long NO_CONSTRUCTION_ID = -1L;

    private final Executor executor;
    private final HintsKeyAccessor keyLoader;
    private final HintsLibrary operations;
    private final HintsSubmissions submissions;
    private final HintsSigningContext signingContext;
    private final Supplier<NodeInfo> selfNodeInfoSupplier;
    private final Supplier<Configuration> configSupplier;

    /**
     * May be null if the node has just started, or if the network has completed the most up-to-date
     * construction implied by its roster store.
     */
    @Nullable
    private HintsController controller;

    @Inject
    public HintsConstructionControllers(
            @NonNull final Executor executor,
            @NonNull final HintsKeyAccessor keyLoader,
            @NonNull final HintsLibrary operations,
            @NonNull final HintsSubmissions submissions,
            @NonNull final HintsSigningContext signingContext,
            @NonNull final Supplier<NodeInfo> selfNodeInfoSupplier,
            @NonNull final Supplier<Configuration> configSupplier) {
        this.executor = requireNonNull(executor);
        this.keyLoader = requireNonNull(keyLoader);
        this.signingContext = signingContext;
        this.operations = requireNonNull(operations);
        this.submissions = requireNonNull(submissions);
        this.configSupplier = requireNonNull(configSupplier);
        this.selfNodeInfoSupplier = requireNonNull(selfNodeInfoSupplier);
    }

    /**
     * Creates a new controller for the given hinTS construction, sourcing its rosters from the given store.
     *
     * @param activeRosters the active rosters
     * @param construction the hinTS construction
     * @return the result of the operation
     */
    public @NonNull HintsController getOrCreateFor(
            @NonNull final ActiveRosters activeRosters,
            @NonNull final HintsConstruction construction,
            @NonNull final ReadableHintsStore hintsStore) {
        requireNonNull(activeRosters);
        requireNonNull(construction);
        requireNonNull(hintsStore);
        if (currentConstructionId() != construction.constructionId()) {
            if (controller != null) {
                controller.cancelPendingWork();
            }
            controller = newControllerFor(activeRosters, construction, hintsStore);
        }
        return requireNonNull(controller);
    }

    /**
     * Returns the in-progress controller for the hinTS construction with the given ID, if it exists.
     *
     * @param constructionId the ID of the hinTS construction
     * @return the controller, if it exists
     */
    public Optional<HintsController> getInProgressById(final long constructionId) {
        return currentConstructionId() == constructionId
                ? Optional.ofNullable(controller).filter(HintsController::isStillInProgress)
                : Optional.empty();
    }

    /**
     * Returns the in-progress controller for the hinTS construction with the given universe size, if it exists.
     *
     * @param m the log2 of the universe size
     * @return the controller, if it exists
     */
    public Optional<HintsController> getInProgressForNumParties(final int m) {
        return Optional.ofNullable(controller).filter(c -> c.hasNumParties(m));
    }

    /**
     * Returns a new controller for the given active rosters and in-progress construction.
     * @param activeRosters the active rosters
     * @param construction the hinTS construction
     * @param hintsStore the hints store
     * @return the controller
     */
    private HintsController newControllerFor(
            @NonNull final ActiveRosters activeRosters,
            @NonNull final HintsConstruction construction,
            @NonNull final ReadableHintsStore hintsStore) {
        final var weights = activeRosters.transitionWeights();
        final var numParties = partySizeForRosterNodeCount(weights.targetRosterSize());
        final var publications = hintsStore.getNodeHintsKeyPublications(
                weights.targetNodeWeights().keySet(), numParties);
        final var votes = hintsStore.votesFor(
                construction.constructionId(), weights.sourceNodeWeights().keySet());
        final var blsKeyPair = keyLoader.getOrCreateBlsKeyPair(construction.constructionId());
        return new HintsController(
                selfNodeInfoSupplier.get().nodeId(),
                construction,
                weights,
                executor,
                blsKeyPair,
                operations,
                publications,
                votes,
                submissions,
                signingContext);
    }

    private long currentConstructionId() {
        return controller != null ? controller.constructionId() : NO_CONSTRUCTION_ID;
    }

    private static @NonNull Map<Long, Long> weightsFrom(@NonNull final Roster roster) {
        return requireNonNull(roster).rosterEntries().stream().collect(toMap(RosterEntry::nodeId, RosterEntry::weight));
    }
}
