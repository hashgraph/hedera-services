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

package com.swirlds.platform.state;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.state.MerkleState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Implements the major lifecycle events for the Merkle state.
 *
 * <p>Currently these are implied by the {@link com.swirlds.platform.system.SwirldState}
 * interface; but in the future will be callbacks registered with a platform builder.
 */
public interface MerkleStateLifecycles {
    /**
     * Called when an event is added to the hashgraph used to compute consensus ordering
     * for this Hedera node.
     *
     * @param event the event that was added
     * @param state the latest immutable state at the time of the event
     */
    void onPreHandle(@NonNull Event event, @NonNull MerkleState state);

    /**
     * Called when a round of events have reached consensus, and are ready to be handled
     * by the Hedera network.
     *
     * @param round the round that has just reached consensus
     * @param platformState the working state of the platform
     * @param state the working state of the network
     */
    void onHandleConsensusRound(@NonNull Round round, @NonNull PlatformState platformState, @NonNull MerkleState state);

    /**
     * Called when the platform is initializing the Hedera network state.
     *
     * @param state the working state of the network to be initialized
     * @param platform the platform used by this node
     * @param platformState the working state of the platform
     * @param trigger the reason for the initialization
     * @param previousVersion if non-null, the Hedera network version that was previously in use
     */
    void onStateInitialized(
            @NonNull MerkleState state,
            @NonNull Platform platform,
            @NonNull PlatformState platformState,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion);

    /**
     * Called when the platform needs to update the weights in the Hedera network
     * address book
     *
     * @param state the working state of the network
     * @param configAddressBook the address book used to configure the network
     * @param context the current platform context
     */
    void onUpdateWeight(
            @NonNull MerkleStateRoot state, @NonNull AddressBook configAddressBook, @NonNull PlatformContext context);

    /**
     * Called when event stream recovery finishes.
     *
     * @param recoveredState the recovered state after reapplying all events
     */
    void onNewRecoveredState(@NonNull MerkleStateRoot recoveredState);
}
