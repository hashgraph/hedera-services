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

import com.swirlds.demo.merkle.map.FCMConfig;
import com.swirlds.demo.platform.freeze.FreezeConfig;
import com.swirlds.demo.platform.iss.IssConfig;
import com.swirlds.demo.platform.nft.config.NftConfig;
import com.swirlds.demo.virtualmerkle.config.VirtualMerkleConfig;

public class SuperConfig {
    private PayloadCfgSimple payloadConfig;
    private SubmitConfig submitConfig;
    private FCMConfig fcmConfig;
    private FreezeConfig freezeConfig;
    private SyntheticBottleneckConfig syntheticBottleneckConfig;
    private IssConfig issConfig;
    private QueryConfig queryConfig;
    private NftConfig nftConfig;
    private TransactionPoolConfig transactionPoolConfig;
    private VirtualMerkleConfig virtualMerkleConfig;
    /** exit JVM after received all test transactions and finished all steps */
    private boolean quitJVMAfterTest = true;

    public PayloadCfgSimple getPayloadConfig() {
        return payloadConfig;
    }

    public void setPayloadConfig(PayloadCfgSimple payloadConfig) {
        this.payloadConfig = payloadConfig;
    }

    public SubmitConfig getSubmitConfig() {
        return submitConfig;
    }

    public void setSubmitConfig(SubmitConfig submitConfig) {
        this.submitConfig = submitConfig;
    }

    public FCMConfig getFcmConfig() {
        if (fcmConfig == null) {
            fcmConfig = new FCMConfig();
        }

        return fcmConfig;
    }

    public void setFcmConfig(FCMConfig fcmConfig) {
        this.fcmConfig = fcmConfig;
    }

    public FreezeConfig getFreezeConfig() {
        return freezeConfig;
    }

    public void setFreezeConfig(FreezeConfig freezeConfig) {
        this.freezeConfig = freezeConfig;
    }

    public SyntheticBottleneckConfig getSyntheticBottleneckConfig() {
        return syntheticBottleneckConfig;
    }

    public void setSyntheticBottleneckConfig(SyntheticBottleneckConfig syntheticBottleneckConfig) {
        this.syntheticBottleneckConfig = syntheticBottleneckConfig;
    }

    public IssConfig getIssConfig() {
        return issConfig;
    }

    public void setIssConfig(IssConfig issConfig) {
        this.issConfig = issConfig;
    }

    public QueryConfig getQueryConfig() {
        return queryConfig;
    }

    public void setQueryConfig(QueryConfig queryConfig) {
        this.queryConfig = queryConfig;
    }

    public NftConfig getNftConfig() {
        return nftConfig;
    }

    public void setNftConfig(final NftConfig nftConfig) {
        this.nftConfig = nftConfig;
    }

    public TransactionPoolConfig getTransactionPoolConfig() {
        return transactionPoolConfig;
    }

    public void setTransactionPoolConfig(TransactionPoolConfig transactionPoolConfig) {
        this.transactionPoolConfig = transactionPoolConfig;
    }

    public VirtualMerkleConfig getVirtualMerkleConfig() {
        return virtualMerkleConfig;
    }

    public void setVirtualMerkleConfig(final VirtualMerkleConfig virtualMerkleConfig) {
        this.virtualMerkleConfig = virtualMerkleConfig;
    }

    public boolean isQuitJVMAfterTest() {
        return quitJVMAfterTest;
    }

    public void setQuitJVMAfterTest(boolean quitJVMAfterTest) {
        this.quitJVMAfterTest = quitJVMAfterTest;
    }
}
