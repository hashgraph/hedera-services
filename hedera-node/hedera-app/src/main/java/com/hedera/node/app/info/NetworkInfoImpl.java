/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.info;

import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.Platform;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides information about the network, including the ledger ID, which may be
 * overridden by configuration in state and cannot be used during state migrations
 * that precede loading configuration sources from state.
 */
@Singleton
public class NetworkInfoImpl extends AbstractNetworkInfoImpl implements NetworkInfo {
    private final Bytes ledgerId;

    @Inject
    public NetworkInfoImpl(
            @NonNull final SelfNodeInfo selfNode,
            @NonNull final Platform platform,
            @NonNull final ConfigProvider configProvider) {
        super(selfNode, platform);
        // Load the ledger ID from configuration
        final var config = configProvider.getConfiguration();
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        ledgerId = ledgerConfig.id();
    }

    @NonNull
    @Override
    public Bytes ledgerId() {
        return ledgerId;
    }
}
