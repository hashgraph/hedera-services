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
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A {@link NetworkInfo} implementation that throws {@link UnsupportedOperationException} for all methods,
 * needed as a non-null placeholder for {@link com.hedera.node.app.services.ServiceMigrator} calls made to
 * initialize platform state before the network information is known.
 */
public enum UnavailableNetworkInfo implements NetworkInfo {
    UNAVAILABLE_NETWORK_INFO;

    @NonNull
    @Override
    public Bytes ledgerId() {
        throw new UnsupportedOperationException("Ledger ID is not available");
    }

    @NonNull
    @Override
    public SelfNodeInfo selfNodeInfo() {
        throw new UnsupportedOperationException("Self node info is not available");
    }

    @NonNull
    @Override
    public List<NodeInfo> addressBook() {
        throw new UnsupportedOperationException("Address book is not available");
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(final long nodeId) {
        throw new UnsupportedOperationException("Node info is not available");
    }

    @Override
    public boolean containsNode(final long nodeId) {
        throw new UnsupportedOperationException("Node info is not available");
    }
}
