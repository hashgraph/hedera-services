/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An interface for Swirlds Platform.
 */
public interface Platform {

    /**
     * Get the platform context, which contains various utilities and services provided by the platform.
     *
     * @return this node's platform context
     */
    @NonNull
    PlatformContext getContext();

    /**
     * Get the notification engine running on this node.
     *
     * @return a notification engine
     */
    @NonNull
    NotificationEngine getNotificationEngine();

    /**
     * Get the Roster
     *
     * @return the roster
     */
    @NonNull
    Roster getRoster();

    /**
     * Get the ID of current node
     *
     * @return node ID
     */
    @NonNull
    NodeId getSelfId();

    /**
     * Get the most recent immutable state. This state may or may not be hashed when it is returned. Wrapper must be
     * closed when use of the state is no longer needed else resources may be leaked.
     *
     * @param reason a short description of why this SignedState is being reserved. Each location where a SignedState is
     *               reserved should attempt to use a unique reason, as this makes debugging reservation bugs easier.
     * @param <T>    the type of the state
     * @return a wrapper around the most recent immutable state
     */
    @NonNull
    <T extends State> AutoCloseableWrapper<T> getLatestImmutableState(@NonNull final String reason);

    /**
     * This method can be called to create a new transaction. If accepted by this method, the newly-created transaction
     * will eventually be embedded inside a newly-created event as long as the node does not shut down before getting
     * around to it. As long as this node is healthy, with high probability the transaction will reach consensus, but
     * this is not a hard guarantee.
     * <p>
     * This method will sometimes reject new transactions. Some (but not necessarily all) causes for a transaction to be
     * rejected by this method:
     *
     * <ul>
     * <li>the node is starting up</li>
     * <li>the node is performing a reconnect</li>
     * <li>the node is preparing for an upgrade</li>
     * <li>the node is unable to submit transactions fast enough and has built up a backlog</li>
     * <li>the node is having health problems</li>
     * </ul>
     *
     * <p>
     * Transactions have a maximum size defined by the setting "transactionMaxBytes". If a transaction larger than
     * this is submitted, this method will always reject it.
     *
     * @param transaction the transaction to handle in binary format (format used is up to the application)
     * @return true if the transaction is accepted, false if it is rejected. Being accepted does not guarantee that the
     * transaction will ever reach consensus, only that this node will make a best-effort attempt to make that happen.
     */
    boolean createTransaction(@NonNull byte[] transaction);

    /**
     * generate signature bytes for given data
     *
     * @param data
     * 		an array of bytes
     * @return signature bytes
     */
    @NonNull
    Signature sign(@NonNull byte[] data);

    /**
     * Start this platform.
     */
    void start();
}
