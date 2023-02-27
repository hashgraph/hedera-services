/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system;

import com.swirlds.common.Releasable;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.events.ConsensusEvent;

/**
 * A Swirld app is defined by creating two classes, one implementing {@link SwirldMain}, and the other
 * {@link SwirldState}, such that: <br>
 * <br>
 * <ul>
 * <li>{@code SwirldState} has a no-argument constructor (called by {@link Platform})</li>
 * <li>All {@code SwirldState} variables are thread-safe and private</li>
 * <li>All {@code SwirldState} methods are synchronized</li>
 * <li>{@code SwirldMain} never modifies an object in {@code SwirldState}</li>
 * </ul>
 * <br>
 * So, if {@code SwirldState} contains an array, and {@code SwirldMain} gets it through a getter method,
 * then the developer is responsible for making sure {@code SwirldMain} never changes the contents of that
 * array. Or the getter can simply return a deep copy of the array instead of the original.
 */
public sealed interface SwirldState extends MerkleNode permits SwirldState1, SwirldState2 {

    /**
     * <p>
     * Initialize a state. Called exactly once each time a node creates/recreates a state (e.g. restart, reconnect,
     * genesis).
     * </p>
     *
     * <p>
     * If applicable, the application should check to see if the address book has changed, and if so those changes
     * should be handled (in the future the address book will not be changed in this method). It may also be convenient
     * for the application to initialize internal data structures at this time.
     * </p>
     *
     * @param platform                the Platform that instantiated this state
     * @param swirldDualState         the dual state instance used by the initialization function
     * @param trigger                 describes the reason why the state was created/recreated
     * @param previousSoftwareVersion the previous version of the software, {@link SoftwareVersion#NO_VERSION} if this
     *                                is genesis or if migrating from code from before the concept of an application
     *                                software version
     */
    default void init(
            final Platform platform,
            final SwirldDualState swirldDualState,
            final InitTrigger trigger,
            final SoftwareVersion previousSoftwareVersion) {
        // Override if needed
    }

    /**
     * Given a round of consensus ordered events, update the state to reflect their effect. Events are iterated in
     * consensus order by the {@link Round#iterator()}. Transactions in each event are iterated in consensus order by
     * the {@link ConsensusEvent#consensusTransactionIterator()}. Transactions in a single event occur after all the
     * transactions in the previous event and before all the transactions in the next event.
     *
     * <p>
     * It is good if this method changes some class variables and then return. It is also OK if it spawns a number of
     * threads that change those variables, then wait until all those threads have ended, and then return. It is even OK
     * for it to create a pool of threads that continue to exist after it returns, as long as it ensures that those
     * threads have finished all their changes before it returns. But it is an error for this method to spawn a thread
     * that will make changes after the method returns and before the next time it is called. If this method creates
     * threads that continue to exist after it returns (in a legal way), then it should clean up those resources when
     * the object has been fully released (see documentation in {@link Releasable}). If the SwirldState extends one of
     * the partial merkle implementations (e.g. PartialMerkleLeaf or PartialNaryMerkleInternal) then it can put that
     * cleanup in the onDestroy() method, which is called when the object becomes fully released.
     *
     * @param round           the round of consensus ordered events to update the state with
     * @param swirldDualState current dualState object which can be read/written by the application
     */
    void handleConsensusRound(final Round round, final SwirldDualState swirldDualState);

    /**
     * {@inheritDoc}
     */
    @Override
    SwirldState copy();
}
