package com.hedera.node.app.history.impl;

import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.node.app.history.ReadableHistoryStore;
import com.hedera.node.app.roster.ActiveRosters;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

import static java.util.Objects.requireNonNull;

@Singleton
public class ProofControllers {
    private static final long NO_CONSTRUCTION_ID = -1L;

    /**
     * May be null if the node has just started, or if the network has completed the most up-to-date
     * construction implied by its roster store.
     */
    @Nullable
    private ProofControllerImpl controller;

    @Inject
    public ProofControllers() {
        // Dagger2
    }

    /**
     * Creates a new controller for the given history proof construction, sourcing its rosters from the given store.
     *
     * @param activeRosters the active rosters
     * @param construction the construction
     * @param historyStore the history store
     * @return the result of the operation
     */
    public @NonNull ProofControllerImpl getOrCreateFor(
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
            controller = newControllerFor(activeRosters, construction, hintsStore);
        }
        return requireNonNull(controller);
    }


    /**
     * Returns the ID of the current proof construction, or {@link #NO_CONSTRUCTION_ID} if there is none.
     */
    private long currentConstructionId() {
        return controller != null ? controller.constructionId() : NO_CONSTRUCTION_ID;
    }
}
