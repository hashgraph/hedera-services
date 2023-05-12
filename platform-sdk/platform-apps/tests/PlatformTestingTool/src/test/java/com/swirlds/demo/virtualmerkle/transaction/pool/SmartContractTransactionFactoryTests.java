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

package com.swirlds.demo.virtualmerkle.transaction.pool;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.demo.platform.HotspotConfiguration;
import com.swirlds.demo.platform.fs.stresstest.proto.VirtualMerkleTransaction;
import com.swirlds.demo.virtualmerkle.config.SmartContractConfig;
import com.swirlds.demo.virtualmerkle.random.PTTRandom;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmartContractTransactionFactoryTests {

    /**
     * This test validates that when we set the {@link HotspotConfiguration} to a certain size, we only get
     * a subset of all available keys, and we always get the same subset.
     */
    @Test
    void buildMethodExecutionTransactiondWithHotspotForAll() {
        final PTTRandom random = new PTTRandom();
        final long nodeId = 0;
        final long smartContractsPerNode = 1_000;
        final SmartContractConfig smartContractConfig = new SmartContractConfig();
        final SmartContractTransactionFactory factory =
                new SmartContractTransactionFactory(random, nodeId, smartContractsPerNode, smartContractConfig, 0);

        for (int index = 0; index < smartContractsPerNode; index++) {
            factory.buildCreateSmartContractTransaction();
        }

        final HotspotConfiguration hotspot = new HotspotConfiguration();
        hotspot.setFrequency(1.0);
        hotspot.setSize(40);

        for (int index = 0; index < 10_000; index++) {
            final VirtualMerkleTransaction.Builder builder = factory.buildMethodExecutionTransaction(hotspot);
            final long contractId = builder.getMethodExecution().getContractId();
            assertTrue(contractId < 40, "Only first 40 contracts are considered but got " + contractId);
        }
    }

    /**
     * This test validates that when we set the {@link HotspotConfiguration} to a certain size, and frequency,
     * we only get a subset of all available contracts, and we always get the same subset for the specified frequency,
     * i.e., with {@code frequency = 0.4} on average at most 40% of the contracts will inside the specified subset.
     */
    @Test
    void buildMethodExecutionTransactiondWithHotspotForSome() {
        final PTTRandom random = new PTTRandom();
        final long nodeId = 0;
        final long smartContractsPerNode = 1_000;
        final SmartContractConfig smartContractConfig = new SmartContractConfig();
        final SmartContractTransactionFactory factory =
                new SmartContractTransactionFactory(random, nodeId, smartContractsPerNode, smartContractConfig, 0);

        for (int index = 0; index < smartContractsPerNode; index++) {
            factory.buildCreateSmartContractTransaction();
        }

        final HotspotConfiguration hotspot = new HotspotConfiguration();
        hotspot.setFrequency(0.40);
        hotspot.setSize(40);

        final List<Long> contracts = new ArrayList<>();
        for (int index = 0; index < 10_000; index++) {
            final VirtualMerkleTransaction.Builder builder = factory.buildMethodExecutionTransaction(hotspot);
            final long contractId = builder.getMethodExecution().getContractId();
            if (contractId > 40) {
                contracts.add(contractId);
            }
        }

        assertTrue(contracts.size() < 6_000, "Expected on average a size less than 6K but got " + contracts.size());
    }
}
