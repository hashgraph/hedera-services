/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;

/**
 * Creates instances of {@link ReconnectLearner}
 */
public class ReconnectLearnerFactory {
    private final Roster roster;
    private final Duration reconnectSocketTimeout;
    private final ReconnectMetrics statistics;
    private final ThreadManager threadManager;
    private final PlatformContext platformContext;

    /**
     * @param platformContext the platform context
     * @param threadManager          responsible for managing thread lifecycles
     * @param roster                 the current roster
     * @param reconnectSocketTimeout the socket timeout to use during the reconnect
     * @param statistics             reconnect metrics
     */
    public ReconnectLearnerFactory(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Roster roster,
            @NonNull final Duration reconnectSocketTimeout,
            @NonNull final ReconnectMetrics statistics) {
        this.platformContext = Objects.requireNonNull(platformContext);
        this.threadManager = Objects.requireNonNull(threadManager);
        this.roster = Objects.requireNonNull(roster);
        this.reconnectSocketTimeout = Objects.requireNonNull(reconnectSocketTimeout);
        this.statistics = Objects.requireNonNull(statistics);
    }

    /**
     * Create an instance of {@link ReconnectLearner}
     *
     * @param conn         the connection to use
     * @param workingState the state to use to perform a delta based reconnect
     * @return a new instance
     */
    public ReconnectLearner create(final Connection conn, final PlatformMerkleStateRoot workingState) {
        return new ReconnectLearner(
                platformContext, threadManager, conn, roster, workingState, reconnectSocketTimeout, statistics);
    }
}
