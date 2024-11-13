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

package com.hedera.node.app.info;

import com.hedera.hapi.node.state.Network;
import java.util.Optional;

/**
 * A {@link StartupAssets} implementation that loads {@link Network} information from a
 * working directory on disk.
 */
public class DiskStartupAssets implements StartupAssets {
    @Override
    public Network genesisNetworkOrThrow() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Optional<Network> overrideNetworkFor(final long roundNumber) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void archiveAssets() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Network migrationNetworkOrThrow() {
        throw new AssertionError("Not implemented");
    }
}
