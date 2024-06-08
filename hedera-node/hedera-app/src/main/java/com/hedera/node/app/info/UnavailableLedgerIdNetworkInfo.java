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

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.Platform;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides information about the network, but NOT the ledger ID, which may be
 * overridden by configuration in state and cannot be used during state migrations
 * that precede loading configuration sources from state.
 */
public class UnavailableLedgerIdNetworkInfo extends AbstractNetworkInfoImpl implements NetworkInfo {
    public UnavailableLedgerIdNetworkInfo(@NonNull final SelfNodeInfo selfNode, @NonNull final Platform platform) {
        super(selfNode, platform);
    }

    @NonNull
    @Override
    public Bytes ledgerId() {
        throw new UnsupportedOperationException("Ledger ID is not available");
    }
}
