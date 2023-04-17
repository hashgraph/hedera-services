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

package com.swirlds.platform.reconnect;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.Connection;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Creates instances of {@link ReconnectLearner}
 */
public class ReconnectLearnerFactory {
    private final AddressBook addressBook;
    private final ReconnectSettings settings;
    private final ReconnectMetrics statistics;
    private final ThreadManager threadManager;
    private final PlatformContext platformContext;

    /**
     * @param platformContext the platform context
     * @param threadManager   responsible for managing thread lifecycles
     * @param addressBook     the current address book
     * @param settings        reconnect settings
     * @param statistics      reconnect metrics
     */
    public ReconnectLearnerFactory(
            @NonNull final PlatformContext platformContext,
            final ThreadManager threadManager,
            final AddressBook addressBook,
            final ReconnectSettings settings,
            final ReconnectMetrics statistics) {
        this.platformContext = Objects.requireNonNull(platformContext);
        this.threadManager = threadManager;
        this.addressBook = addressBook;
        this.settings = settings;
        this.statistics = statistics;
    }

    /**
     * Create an instance of {@link ReconnectLearner}
     *
     * @param conn         the connection to use
     * @param workingState the state to use to perform a delta based reconnect
     * @return a new instance
     */
    public ReconnectLearner create(final Connection conn, final State workingState) {
        return new ReconnectLearner(
                platformContext,
                threadManager,
                conn,
                addressBook,
                workingState,
                settings.getAsyncStreamTimeoutMilliseconds(),
                statistics);
    }
}
