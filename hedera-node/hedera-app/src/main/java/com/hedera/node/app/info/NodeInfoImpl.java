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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

public record NodeInfoImpl(
        long nodeId,
        @NonNull AccountID accountId,
        long stake,
        List<ServiceEndpoint> gossipEndpoints,
        @Nullable Bytes sigCertBytes)
        implements NodeInfo {
    @NonNull
    public static NodeInfo fromRosterEntry(@NonNull final RosterEntry rosterEntry, @NonNull final Node node) {
        return new NodeInfoImpl(
                rosterEntry.nodeId(),
                node.accountId(),
                rosterEntry.weight(),
                rosterEntry.gossipEndpoint(),
                node.gossipCaCertificate());
    }
}
