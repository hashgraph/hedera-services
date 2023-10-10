/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.state;

import com.swirlds.platform.components.PlatformComponent;
import com.swirlds.platform.components.common.output.NewSignedStateFromTransactionsConsumer;
import com.swirlds.platform.components.common.output.SignedStateToLoadConsumer;
import com.swirlds.platform.components.state.query.LatestSignedStateProvider;
import com.swirlds.platform.state.signed.*;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;

/**
 * This component responsible for:
 * <ul>
 *     <li>Managing signed states in memory</li>
 *     <li>Writing signed states to disk</li>
 *     <li>Producing signed state signatures</li>
 *     <li>Collecting signed state signatures</li>
 *     <li>Making certain signed states available for queries</li>
 *     <li>Finding signed states compatible with an emergency state</li>
 * </ul>
 */
public interface StateManagementComponent
        extends PlatformComponent,
                SignedStateFinder,
                SignedStateToLoadConsumer,
                NewSignedStateFromTransactionsConsumer,
                LatestSignedStateProvider {

    /**
     * Get a reserved instance of the latest immutable signed state. May be unhashed, may or may not have all required
     * signatures. State is returned with a reservation.
     *
     * @param reason a short description of why this SignedState is being reserved. Each location where a SignedState is
     *               reserved should attempt to use a unique reason, as this makes debugging reservation bugs easier.
     * @return a reserved signed state, may contain null if none currently in memory are complete
     */
    ReservedSignedState getLatestImmutableState(@NonNull final String reason);

    /**
     * Returns the latest round for which there is a complete signed state.
     *
     * @return the latest round number
     */
    long getLastCompleteRound();

    /**
     * Get the latest signed states stored by this component. This method creates a copy, so no changes to the array
     * will be made.
     * <p>
     * This method is NOT thread safe.
     *
     * @return a copy of the latest signed states
     */
    @Deprecated
    List<SignedStateInfo> getSignedStateInfo();

    /**
     * Dump the latest immutable state if it is available.
     *
     * @param reason   the reason why the state is being dumped
     * @param blocking if true then block until the state dump is complete
     */
    void dumpLatestImmutableState(@NonNull StateToDiskReason reason, boolean blocking);

    /**
     * Get the consensus timestamp of the first state ingested by the signed state manager. Useful for computing the
     * total consensus time that this node has been operating for.
     *
     * @return the consensus timestamp of the first state ingested by the signed state manager, or null if no states
     * have been ingested yet
     */
    @Nullable
    Instant getFirstStateTimestamp();

    /**
     * Get the round of the first state ingested by the signed state manager. Useful for computing the total number of
     * elapsed rounds since startup.
     *
     * @return the round of the first state ingested by the signed state manager, or -1 if no states have been ingested
     */
    long getFirstStateRound();

    /**
     * Get the round of the latest state written to disk, or {@link com.swirlds.common.system.UptimeData#NO_ROUND} if no
     * states have been written to disk since booting up.
     *
     * @return the latest saved state round
     */
    long getLatestSavedStateRound();

    /**
     * Get the signed state manager.
     */
    @NonNull
    SignedStateManager getSignedStateManager();
}
