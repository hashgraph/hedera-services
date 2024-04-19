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

import static com.hedera.node.app.spi.fixtures.state.TestSchema.CURRENT_VERSION;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.info.SelfNodeInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;
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
                    "333.333.333.333",
                    50233,
                    "3333333333333333333333333333333333333333333333333333333333333333",
                    "Alpha"),
            fakeInfoWith(
                    4L,
                    AccountID.newBuilder().accountNum(4).build(),
                    40,
                    "444.444.444.444",
                    50244,
                    "444444444444444444444444444444444444444444444444444444444444444",
                    "Bravo"),
            fakeInfoWith(
                    8L,
                    AccountID.newBuilder().accountNum(5).build(),
                    50,
                    "555.555.555.555",
                    50255,
                    "555555555555555555555555555555555555555555555555555555555555555",
                    "Charlie"));

    @NonNull
    @Override
    public Bytes ledgerId() {
        return DEV_LEDGER_ID;
    }

    @NonNull
    @Override
    public SelfNodeInfo selfNodeInfo() {
        return new SelfNodeInfo() {
            @NonNull
            @Override
            public SemanticVersion hapiVersion() {
                return CURRENT_VERSION;
            }

            @NonNull
            @Override
            public SemanticVersion appVersion() {
                return CURRENT_VERSION;
            }

            @Override
            public boolean zeroStake() {
                return FAKE_NODE_INFOS.get(0).zeroStake();
            }

            @Override
            public long nodeId() {
                return FAKE_NODE_INFOS.get(0).nodeId();
            }

            @Override
            public AccountID accountId() {
                return FAKE_NODE_INFOS.get(0).accountId();
            }

            @Override
            public String memo() {
                return FAKE_NODE_INFOS.get(0).memo();
            }

            @Override
            public String externalHostName() {
                return FAKE_NODE_INFOS.get(0).externalHostName();
            }

            @Override
            public int externalPort() {
                return FAKE_NODE_INFOS.get(0).externalPort();
            }

            @Override
            public String hexEncodedPublicKey() {
                return FAKE_NODE_INFOS.get(0).hexEncodedPublicKey();
            }

            @Override
            public long stake() {
                return FAKE_NODE_INFOS.get(0).stake();
            }
        };
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

    private static NodeInfo fakeInfoWith(
            final long nodeId,
            @NonNull final AccountID nodeAccountId,
            long stake,
            @NonNull String externalHostName,
            int externalPort,
            @NonNull String hexEncodedPublicKey,
            @NonNull String memo) {
        return new NodeInfo() {
            @Override
            public long nodeId() {
                return nodeId;
            }

            @Override
            public String memo() {
                return memo;
            }

            @Override
            public AccountID accountId() {
                return nodeAccountId;
            }

            @Override
            public String externalHostName() {
                return externalHostName;
            }

            @Override
            public int externalPort() {
                return externalPort;
            }

            @Override
            public String hexEncodedPublicKey() {
                return hexEncodedPublicKey;
            }

            @Override
            public long stake() {
                return stake;
            }
        };
    }
}
