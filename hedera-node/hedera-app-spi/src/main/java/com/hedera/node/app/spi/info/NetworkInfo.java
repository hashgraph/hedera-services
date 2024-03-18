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

package com.hedera.node.app.spi.info;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Provides information about the network.
 */
public interface NetworkInfo {

    /**
     * Returns the current ledger ID.
     *
     * @return the {@link Bytes} of the current ledger ID
     */
    @NonNull
    Bytes ledgerId();

    @NonNull
    SelfNodeInfo selfNodeInfo();

    @NonNull
    List<NodeInfo> addressBook();

    @Nullable
    NodeInfo nodeInfo(long nodeId);

    /**
     * Returns true if the network contains a node with the given ID.
     * @param nodeId the ID of the node to check for
     * @return true if the network contains a node with the given ID
     */
    boolean containsNode(long nodeId);
}
