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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.Network;
import com.hedera.node.app.info.StartupNetworks;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.config.ConfigProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/**
 * A fake implementation of {@link StartupNetworks}.
 */
public class FakeStartupNetworks implements StartupNetworks {
    public FakeStartupNetworks(
            final long selfNodeId,
            @NonNull final ConfigProvider configProvider,
            @NonNull final TssBaseService tssBaseService,
            @NonNull final TssDirectoryFactory tssDirectoryFactory) {
        requireNonNull(configProvider);
        requireNonNull(tssBaseService);
        requireNonNull(tssDirectoryFactory);
    }

    @Override
    public Network genesisNetworkOrThrow() {
        // FUTURE - base this network on a fake roster
        return Network.DEFAULT;
    }

    @Override
    public Optional<Network> overrideNetworkFor(final long roundNumber) {
        return Optional.empty();
    }

    @Override
    public void setOverrideRound(final long roundNumber) {
        // No-op
    }

    @Override
    public void archiveJsonFiles() {
        // No-op
    }

    @Override
    public Network migrationNetworkOrThrow() {
        return Network.DEFAULT;
    }
}
