/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.common.test.threading.SyncPhaseParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.Connection;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;

/**
 * This class initiates a sync between a caller and listener node.
 */
public class Synchronizer {

    // The parallel executor used to execute the caller's and listener's synchronize at the same time
    private final ParallelExecutor parallelExecutor;

    public Synchronizer() {
        parallelExecutor = new SyncPhaseParallelExecutor(getStaticThreadManager(), null, null, false);
    }

    /**
     * Performs synchronization between the caller and listener nodes.
     *
     * The {@link ShadowGraphSynchronizer#synchronize(Connection)} method is
     * invoked on each node in parallel using the {@link ParallelExecutor}.
     *
     * @throws Exception
     * 		is there is any exception during connection setup ornode  synchronization
     */
    public void synchronize(final SyncNode caller, final SyncNode listener) throws Exception {

        parallelExecutor.doParallel(
                () -> {
                    try {
                        final boolean synchronize = caller.getSynchronizer().synchronize(caller.getConnection());
                        caller.setSynchronizerReturn(synchronize);
                    } catch (final Exception e) {
                        caller.setSynchronizerReturn(null);
                        caller.setSyncException(e);
                        caller.getConnection().disconnect();
                        throw e;
                    }
                    // ignored
                    return null;
                },
                () -> {
                    if (listener.isSendRecInitBytes()) {
                        // Read the COMM_SYNC_REQUEST byte on the listener prior to calling synchronize to match the
                        // production code and align the streams
                        listener.getConnection().getDis().readByte();
                    }
                    try {
                        if (listener.isCanAcceptSync()) {
                            final boolean synchronize =
                                    listener.getSynchronizer().synchronize(listener.getConnection());
                            listener.setSynchronizerReturn(synchronize);
                        } else {
                            listener.getSynchronizer().rejectSync(listener.getConnection());
                        }
                    } catch (final Exception e) {
                        listener.setSynchronizerReturn(null);
                        listener.setSyncException(e);
                        listener.getConnection().disconnect();
                        throw e;
                    }
                    // ignored
                    return null;
                });
    }
}
