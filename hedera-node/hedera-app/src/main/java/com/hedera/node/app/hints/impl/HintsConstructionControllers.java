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

package com.hedera.node.app.hints.impl;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.node.app.hints.HintsService;
import com.swirlds.platform.state.service.ReadableRosterStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Logic to get or create a {@link HintsConstructionController} for source/target roster hashes
 * relative to the current {@link HintsService} state and consensus time. We only support one
 * controller at a time, so if this class detects that a new controller is needed, it will cancel
 * any existing controller via {@link HintsConstructionController#cancelPendingWork()} and release its
 * reference to the cancelled controller.
 */
@Singleton
public class HintsConstructionControllers {
    /**
     * May be null if the node has just started, or if the network has complete the most up-to-date
     * construction implied by its roster store.
     */
    @Nullable
    private HintsConstructionController controller;

    @Inject
    public HintsConstructionControllers() {
        // Dagger2
    }

    /**
     * Creates a new controller for the given hinTS construction, sourcing its rosters from the given store.
     * @param construction the hinTS construction
     * @param rosterStore the store to source rosters from
     * @return the result of the operation
     */
    public HintsConstructionController getOrCreateControllerFor(
            @NonNull final HintsConstruction construction, @NonNull final ReadableRosterStore rosterStore) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Returns the controller for the hinTS construction with the given ID, if it exists.
     * @param constructionId the ID of the hinTS construction
     * @return the controller, if it exists
     */
    public Optional<HintsConstructionController> getControllerById(final long constructionId) {
        throw new AssertionError("Not implemented");
    }
}
