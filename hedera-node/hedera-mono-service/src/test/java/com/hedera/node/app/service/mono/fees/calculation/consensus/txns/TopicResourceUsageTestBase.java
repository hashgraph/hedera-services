/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.fees.calculation.consensus.txns;

import static com.hedera.test.utils.IdUtils.asTopic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.merkle.map.MerkleMap;

class TopicResourceUsageTestBase {
    protected static final int totalSigCount = 1;
    protected static final int payerAcctSigCount = 2;
    protected static final int signatureSize = 64;

    // Base services RBH when even when no extra transaction specific rbs is charged.
    protected static final int baseServicesRbh = 6;
    // Base network RBH when even when no extra transaction specific rbs is charged.
    protected static final int baseNetworkRbh = 1;
    protected static final int nodeBpr = 4; // always equal to INT_SIZE
    protected static final int baseBpt = 140; // size of transaction fields and sigs

    protected StateView view;
    protected MerkleMap<EntityNum, MerkleTopic> topics;
    protected TopicID topicId = asTopic("0.0.1234");
    protected SigValueObj sigValueObj =
            new SigValueObj(totalSigCount, payerAcctSigCount, signatureSize);
    protected NodeLocalProperties nodeProps;

    void setup() throws Throwable {
        topics = mock(MerkleMap.class);
        nodeProps = mock(NodeLocalProperties.class);
        final MutableStateChildren children = new MutableStateChildren();
        children.setTopics(topics);
        view = new StateView(null, children, null);
    }

    protected void checkServicesFee(final FeeData feeData, final int extraRbh) {
        // Only rbh component is non-zero in services FeeComponents.
        checkFeeComponents(feeData.getServicedata(), 0, 0, baseServicesRbh + extraRbh, 0);
    }

    protected void checkNetworkFee(final FeeData feeData, final int extraBpt, final int extraRbh) {
        checkFeeComponents(
                feeData.getNetworkdata(),
                baseBpt + extraBpt,
                totalSigCount,
                baseNetworkRbh + extraRbh,
                0);
    }

    protected void checkNodeFee(final FeeData feeData, final int extraBpt) {
        checkFeeComponents(
                feeData.getNodedata(), baseBpt + extraBpt, payerAcctSigCount, 0, nodeBpr);
    }

    protected void checkFeeComponents(
            final FeeComponents actual,
            final int bpt,
            final int vpt,
            final int rbh,
            final int bpr) {
        final FeeComponents expected =
                FeeComponents.newBuilder()
                        .setConstant(1)
                        .setBpt(bpt)
                        .setVpt(vpt)
                        .setRbh(rbh)
                        .setBpr(bpr)
                        .build(); // other components are always 0 for topic transactions
        assertEquals(expected, actual);
    }
}
