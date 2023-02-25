/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.chatter;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.platform.Connection;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.sync.FallenBehindManager;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import com.swirlds.platform.sync.SyncException;
import java.io.IOException;
import java.util.List;

/**
 * Exchanges non-expired events with the peer and ensures all future events will be sent through {@link
 * com.swirlds.platform.chatter.communication.ChatterProtocol}
 */
public class ChatterSyncProtocol implements Protocol {
    private final NodeId peerId;
    private final CommunicationState state;
    private final MessageProvider messageProvider;
    private final ShadowGraphSynchronizer synchronizer;
    private final FallenBehindManager fallenBehindManager;

    /**
     * @param state
     * 		the state that tracks the peer
     * @param messageProvider
     * 		keeps messages that need to be sent to the chatter peer
     * @param synchronizer
     * 		does a sync and enables chatter
     * @param fallenBehindManager
     * 		maintains this node's behind status and the peers that have informed this node that it is behind
     */
    public ChatterSyncProtocol(
            final NodeId peerId,
            final CommunicationState state,
            final MessageProvider messageProvider,
            final ShadowGraphSynchronizer synchronizer,
            final FallenBehindManager fallenBehindManager) {
        this.peerId = peerId;
        this.state = state;
        this.messageProvider = messageProvider;
        this.synchronizer = synchronizer;
        this.fallenBehindManager = fallenBehindManager;
    }

    @Override
    public boolean shouldInitiate() {
        // if we are out of sync, we should initiate
        return state.isOutOfSync() && fallenBehindSync();
    }

    @Override
    public boolean shouldAccept() {
        // we should accept even if we are in sync, so long as chattering is not suspended
        return !state.isSuspended() && fallenBehindSync();
    }

    /**
     * Checks our fallen behind status and decides if we should sync with this neighbor.
     * If someone told us we have fallen behind, we should not sync with them again until we have:
     * <ul>
     * <li>
     * decided that we have fallen behind and then reconnect
     * </li>
     * <li>
     * someone else tells us we have not fallen behind
     * </li>
     * </ul>
     *
     * @return true if we should sync with this neighbor
     */
    private boolean fallenBehindSync() {
        final List<Long> notReportedFallenBehind = fallenBehindManager.getNeededForFallenBehind();
        return notReportedFallenBehind == null || notReportedFallenBehind.contains(peerId.getId());
    }

    @Override
    public boolean acceptOnSimultaneousInitiate() {
        return true;
    }

    @Override
    public void runProtocol(final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {
        state.chatterSyncStarted();
        try {
            if (synchronizer.synchronize(connection)) {
                state.chatterSyncSucceeded();
            } else {
                state.chatterSyncFailed();
            }
        } catch (final ParallelExecutionException | SyncException e) {
            state.chatterSyncFailed();
            messageProvider.clear();
            if (Utilities.isRootCauseSuppliedType(e, IOException.class)) {
                throw new IOException(e);
            }
            throw new NetworkProtocolException(e);
        } catch (final IOException | InterruptedException | RuntimeException e) {
            state.chatterSyncFailed();
            messageProvider.clear();
            throw e;
        }
    }
}
