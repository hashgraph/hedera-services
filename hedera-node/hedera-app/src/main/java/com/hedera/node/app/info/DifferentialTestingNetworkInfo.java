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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.info.SelfNodeInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class DifferentialTestingNetworkInfo implements NetworkInfo {
    private static final Logger logger = LogManager.getLogger(DifferentialTestingNetworkInfo.class);
    private final Map<NodeId, NodeInfo> mainnetNodeInfos = LongStream.range(0L, 29L)
            .boxed()
            .collect(Collectors.toMap(
                    NodeId::new,
                    nodeId -> new NodeInfoImpl(
                            nodeId,
                            AccountID.newBuilder().accountNum(nodeId + 3).build(),
                            1L,
                            "node" + nodeId,
                            123,
                            "<not-a-key>",
                            "0.0." + (nodeId + 3))));

    private final SelfNodeInfo selfNode;

    @Inject
    public DifferentialTestingNetworkInfo(@NonNull final SelfNodeInfo selfNode) {
        this.selfNode = selfNode;
    }

    @NonNull
    @Override
    public Bytes ledgerId() {
        return Bytes.wrap(new byte[] {0x00});
    }

    @NonNull
    @Override
    public SelfNodeInfo selfNodeInfo() {
        return selfNode;
    }

    @NonNull
    @Override
    public List<NodeInfo> addressBook() {
        return new ArrayList<>(mainnetNodeInfos.values());
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(long nodeId) {
        // logger.info("DIFF-TEST: expected. Checking nodeId {}", nodeId);
        return mainnetNodeInfos.get(new NodeId(nodeId));
    }
}
