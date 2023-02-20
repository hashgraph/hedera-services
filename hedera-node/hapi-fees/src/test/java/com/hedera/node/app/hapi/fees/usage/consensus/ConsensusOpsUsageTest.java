/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.fees.usage.consensus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.hapi.fees.test.AdapterUtils;
import com.hedera.node.app.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import org.junit.jupiter.api.Test;

class ConsensusOpsUsageTest {
    private static final int numSigs = 3;
    private static final int sigSize = 100;
    private static final int numPayerKeys = 1;
    private static final String memo = "The commonness of thoughts and images";
    private static final String message = "That have the frenzy of our Western seas";
    private static final SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);

    private static final ConsensusOpsUsage subject = new ConsensusOpsUsage();

    @Test
    void matchesLegacyEstimate() {
        final var expected = FeeData.newBuilder()
                .setNetworkdata(FeeComponents.newBuilder()
                        .setConstant(1)
                        .setBpt(277)
                        .setVpt(3)
                        .setRbh(4))
                .setNodedata(FeeComponents.newBuilder()
                        .setConstant(1)
                        .setBpt(277)
                        .setVpt(1)
                        .setBpr(4))
                .setServicedata(FeeComponents.newBuilder().setConstant(1).setRbh(8))
                .build();
        final var accum = new UsageAccumulator();
        final var baseMeta = new BaseTransactionMeta(memo.length(), 0);
        final var submitMeta = new SubmitMessageMeta(message.length());

        subject.submitMessageUsage(sigUsage, submitMeta, baseMeta, accum);
        final var actualLegacyRepr = AdapterUtils.feeDataFrom(accum);

        assertEquals(expected, actualLegacyRepr);
    }
}
