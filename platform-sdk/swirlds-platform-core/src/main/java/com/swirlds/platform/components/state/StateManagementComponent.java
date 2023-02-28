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

import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.components.PlatformComponent;
import com.swirlds.platform.components.common.output.NewSignedStateFromTransactionsConsumer;
import com.swirlds.platform.components.common.output.RoundAppliedToStateConsumer;
import com.swirlds.platform.components.common.output.SignedStateToLoadConsumer;
import com.swirlds.platform.components.common.output.StateSignatureConsumer;
import com.swirlds.platform.components.state.query.LatestSignedStateProvider;
import com.swirlds.platform.reconnect.emergency.EmergencyStateFinder;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateInfo;
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
                EmergencyStateFinder,
                StateSignatureConsumer,
                RoundAppliedToStateConsumer,
                SignedStateToLoadConsumer,
                NewSignedStateFromTransactionsConsumer,
                LatestSignedStateProvider {

    /**
     * Get a wrapper containing the latest immutable signed state. May be unhashed, may or may not have all required
     * signatures. State is returned with a reservation.
     *
     * @return a wrapper with the latest signed state, or null if none are complete
     */
    AutoCloseableWrapper<SignedState> getLatestImmutableState();

    /**
     * Returns the latest round for which there is a complete signed state.
     *
     * @return the latest round number
     */
    long getLastCompleteRound();

    /**
     * Get the last round for which a signed state was saved to disk.
     *
     * @return the last round that was saved to disk, or -1 if no round was recently saved to disk
     */
    long getLastRoundSavedToDisk();

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
}
