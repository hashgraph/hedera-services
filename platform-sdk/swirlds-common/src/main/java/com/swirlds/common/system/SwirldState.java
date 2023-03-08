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

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;

/**
 * A Swirld app is defined by creating two classes, one implementing {@link SwirldMain}, and the other
 * {@link SwirldState}. The class that implements the SwirldState should have a zero-argument constructor.
 */
public interface SwirldState extends MerkleNode {

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
     * Provides the application an opportunity to perform operations on transactions in an event prior to handling.
     * Called against a given {@link Event} only once, globally (not once per state instance). This method may modify
     * the {@link Transaction}s in the event, but such modification is not necessary.
     * <p>
     * No ordering guarantees are given for this method.
     * The only guarantee is that for all events that reach consensus,
     * this method will eventually be called as long as the node does not crash.
     * <ul>
     * <li>
     * This method is usually invoked on an event before it reaches consensus,
     * but that is not a hard guarantee.
     * </li>
     * <li>
     * This method may be called after {@link #handleConsensusRound(Round, SwirldDualState)} has been called on that
     * event's round (although such behavior is expected to be uncommon).
     * <ul>
     * <li>
     * If it is desired that pre-handle always come before handle for a given event, then the application should
     * implement its own ordering mechanism that causes handle to wait until pre-handle has completed.
     * </li>
     * </ul>
     * </li>
     * <li>
     * The order that events may be passed to this method may be different from their eventual consensus order.
     * </li>
     * <li>
     * It is possible for this method to be called on an event, but for that
     * event never to reach consensus (thus becoming stale).
     * </li>
     * <li>
     * For events that become stale, this method may be called on some nodes but not others.
     * </li>
     * </ul>
     *
     * <p>
     * <strong>This method is always invoked on an immutable state. </strong> Which immutable state copy
     * is not guaranteed, although it will always be a recent copy.
     *
     * @param event the event to perform pre-handling on
     * @see #handleConsensusRound(Round, SwirldDualState)
     */
    default void preHandle(final Event event) {
        // Override if needed
    }

    /**
     * Handle transactions in a round apply them to the state.
     * This method is always called on the mutable state, i.e. the most recent copy.
     * <p>
     * After this method returns, all side effects of the transactions in this round must be written to the state.
     * It is not ok to asynchronously modify the state after this method returns.
     * <p>
     * All modifications to the state in this method must be deterministic. That is, the state that results
     * from applying this method must be guaranteed to always have the same cryptographic merkle hash.
     * <p>
     * This method is called on all rounds in sequential order. This method is not called for round N+1 until round
     * N has been fully handled. Between each call of this method, the state will be fast copied. Each copy of the
     * state will only have this method called on it once.
     * <p>
     * With the exception of the {@link #init(Platform, SwirldDualState, InitTrigger, SoftwareVersion)} method
     * and serialization migration hooks, it is NEVER ok to modify the state except in this method.
     *
     * @param round           the round to be handled and applied to this state
     * @param swirldDualState the dual state for this round
     */
    void handleConsensusRound(final Round round, final SwirldDualState swirldDualState);

    /**
     * {@inheritDoc}
     */
    @Override
    SwirldState copy();
}
