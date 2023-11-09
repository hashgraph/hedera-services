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

package com.swirlds.platform.gossip;

import com.swirlds.base.state.Lifecycle;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This object is responsible for talking to other nodes and distributing events.
 */
public interface Gossip extends Clearable, ConnectionTracker, Lifecycle {

    /**
     * Load data from a signed state.
     *
     * @param signedState the signed state to load from
     */
    void loadFromSignedState(@NonNull final SignedState signedState);

    /**
     * This method is called when the node has finished a reconnect.
     */
    void resetFallenBehind();

    /**
     * Check if we have fallen behind.
     *
     * @return true if we have fallen behind
     */
    boolean hasFallenBehind();

    /**
     * {@inheritDoc}
     */
    @Override
    void clear();

    /**
     * Get the number of active connections.
     * @return the number of active connections
     */
    int activeConnectionNumber();

    /**
     * Stop gossiping until {@link #resume()} is called. If called when already paused then this has no effect.
     */
    void pause();

    /**
     * Resume gossiping. If called when already running then this has no effect.
     */
    void resume();
}
