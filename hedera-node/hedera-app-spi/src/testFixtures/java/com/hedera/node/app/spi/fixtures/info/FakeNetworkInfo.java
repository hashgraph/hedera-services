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

package com.hedera.node.app.spi.fixtures.info;

import static com.swirlds.platform.system.address.AddressBookUtils.endpointFor;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;
import com.swirlds.state.State;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Holds fake network information for testing.
 */
public class FakeNetworkInfo implements NetworkInfo {
    private static final Bytes DEV_LEDGER_ID = Bytes.wrap(new byte[] {0x03});
    private static final List<NodeId> FAKE_NODE_INFO_IDS = List.of(new NodeId(2), new NodeId(4), new NodeId(8));
    private static final List<NodeInfo> FAKE_NODE_INFOS = List.of(
            fakeInfoWith(
                    2L,
                    AccountID.newBuilder().accountNum(3).build(),
                    30,
                    List.of(endpointFor("333.333.333.333", 50233), endpointFor("127.0.0.1", 20)),
                    Bytes.wrap("cert1")),
            fakeInfoWith(
                    4L,
                    AccountID.newBuilder().accountNum(4).build(),
                    40,
                    List.of(endpointFor("444.444.444.444", 50244), endpointFor("127.0.0.2", 21)),
                    Bytes.wrap("cert2")),
            fakeInfoWith(
                    8L,
                    AccountID.newBuilder().accountNum(5).build(),
                    50,
                    List.of(endpointFor("555.555.555.555", 50255), endpointFor("127.0.0.3", 22)),
                    Bytes.wrap("cert3")));

    @NonNull
    @Override
    public Bytes ledgerId() {
        return DEV_LEDGER_ID;
    }

    @NonNull
    @Override
    public NodeInfo selfNodeInfo() {
        return FAKE_NODE_INFOS.get(0);
    }

    @NonNull
    @Override
    public List<NodeInfo> addressBook() {
        return FAKE_NODE_INFOS;
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(long nodeId) {
        return FAKE_NODE_INFOS.get((int) nodeId);
    }

    @Override
    public boolean containsNode(final long nodeId) {
        return FAKE_NODE_INFO_IDS.contains(new NodeId(nodeId));
    }

    @Override
    public void updateFrom(final State state) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private static NodeInfo fakeInfoWith(
            final long nodeId,
            @NonNull final AccountID nodeAccountId,
            long stake,
            List<ServiceEndpoint> gossipEndpoints,
            @Nullable Bytes sigCertBytes) {
        return new NodeInfo() {
            @Override
            public long nodeId() {
                return nodeId;
            }

            @Override
            public AccountID accountId() {
                return nodeAccountId;
            }

            @Override
            public long stake() {
                return stake;
            }

            @Override
            public Bytes sigCertBytes() {
                return sigCertBytes;
            }

            @Override
            public List<ServiceEndpoint> gossipEndpoints() {
                return gossipEndpoints;
            }
        };
    }
}
