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

package com.swirlds.demo.platform;

import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.demo.merkle.map.internal.ExpectedFCMFamily;

/*
 * Simulate Hedera Client to access file or map without going through
 * consensue
 */
public class AppClient extends Thread {

    Platform platform;
    long selfId;
    String myName;
    byte[] submittedBytes = null;
    PayloadCfgSimple config;
    /** generate different payload bytes according to config */
    private TransactionPool transactionPool;

    private TransactionSubmitter submitter;
    private ExpectedFCMFamily expectedFCMFamily;

    AppClient(Platform platform, long selfId, SuperConfig currentConfig, String myName) {
        this.platform = platform;
        this.myName = myName;
        this.selfId = selfId;

        PayloadCfgSimple pConfig = currentConfig.getPayloadConfig();

        PayloadConfig payloadConfig = PayloadConfig.builder()
                .setAppendSig(pConfig.isAppendSig())
                .setInsertSeq(pConfig.isInsertSeq())
                .setVariedSize(pConfig.isVariedSize())
                .setPayloadByteSize(pConfig.getPayloadByteSize())
                .setMaxByteSize(pConfig.getMaxByteSize())
                .setType(pConfig.getType())
                .setDistribution(pConfig.getDistribution())
                .build();

        SubmitConfig submitConfig = currentConfig.getSubmitConfig();

        final PlatformTestingToolState state = ((PlatformWithDeprecatedMethods) platform).getState();
        try {
            submitter = new TransactionSubmitter(submitConfig, state.getControlQuorum());
            expectedFCMFamily = state.getStateExpectedMap();
        } finally {
            ((PlatformWithDeprecatedMethods) platform).releaseState();
        }

        transactionPool = new TransactionPool(
                platform,
                platform.getSelfId().getId(),
                payloadConfig,
                myName,
                currentConfig.getFcmConfig(),
                currentConfig.getVirtualMerkleConfig(),
                currentConfig.getFreezeConfig(),
                currentConfig.getTransactionPoolConfig(),
                submitter,
                expectedFCMFamily);

        config = pConfig;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        } // wait a while to let enough file or fcm available
    }
}
