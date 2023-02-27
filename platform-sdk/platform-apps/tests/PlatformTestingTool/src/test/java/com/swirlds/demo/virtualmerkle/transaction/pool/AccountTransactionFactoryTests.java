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

import com.swirlds.demo.merkle.map.internal.DummyExpectedFCMFamily;
import com.swirlds.demo.merkle.map.internal.ExpectedFCMFamily;
import com.swirlds.demo.platform.HotspotConfiguration;
import com.swirlds.demo.platform.fs.stresstest.proto.VirtualMerkleTransaction;
import com.swirlds.demo.virtualmerkle.random.PTTRandom;
import com.swirlds.merkle.map.test.pta.MapKey;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

class AccountTransactionFactoryTests {

    /**
     * This test validates that when we set the {@link HotspotConfiguration} to a certain size, we only get
     * a subset of all available keys, and we always get the same subset.
     *
     * For this case, we use a {@code samplingProbability = 1.0} to always use the already generated keys to
     * generate the next one when we build the create accounts, and we can validate that keys update are part
     * of the expected subset.
     */
    @Test
    void buildUpdateAccountTransactionWithHotspotForAll() {
        final PTTRandom random = new PTTRandom();
        final long nodeId = 0;
        final double samplingProbability = 1.0;
        final ExpectedFCMFamily expectedFCMFamily = new DummyExpectedFCMFamily(nodeId);
        final AccountTransactionFactory factory =
                new AccountTransactionFactory(random, samplingProbability, expectedFCMFamily, nodeId, 1_000, 0);

        for (int index = 0; index < 1_000; index++) {
            factory.buildCreateAccountTransaction();
        }

        final HotspotConfiguration hotspot = new HotspotConfiguration();
        hotspot.setFrequency(1.0);
        hotspot.setSize(20);

        for (int index = 0; index < 10_000; index++) {
            final Pair<MapKey, VirtualMerkleTransaction.Builder> pair = factory.buildUpdateAccountTransaction(hotspot);
            final MapKey key = pair.getKey();
            assertTrue(key.getAccountId() < 20, "Only first 20 accounts are considered but got " + key.getAccountId());
        }
    }

    /**
     * This test validates that when we set the {@link HotspotConfiguration} to a certain size, and frequency,
     * we only get a subset of all available keys, and we always get the same subset for the specified frequency,
     * i.e., with {@code frequency = 0.4} on average at most 40% of the keys will inside the specified subset.
     *
     * For this case, we use a {@code samplingProbability = 1.0} to always use the already generated keys to
     * generate the next one when we build the create accounts, and we can validate that 60% of keys fall out
     * of the specified subset.
     */
    @Test
    void buildUpdateAccountTransactionWithHotspotForSome() {
        final PTTRandom random = new PTTRandom();
        final long nodeId = 0;
        final double samplingProbability = 1.0;
        final ExpectedFCMFamily expectedFCMFamily = new DummyExpectedFCMFamily(nodeId);
        final AccountTransactionFactory factory =
                new AccountTransactionFactory(random, samplingProbability, expectedFCMFamily, nodeId, 1_000, 0);

        for (int index = 0; index < 1_000; index++) {
            factory.buildCreateAccountTransaction();
        }

        final HotspotConfiguration hotspot = new HotspotConfiguration();
        hotspot.setFrequency(0.4);
        hotspot.setSize(20);

        final List<Long> accounts = new ArrayList<>();
        for (int index = 0; index < 10_000; index++) {
            final Pair<MapKey, VirtualMerkleTransaction.Builder> pair = factory.buildUpdateAccountTransaction(hotspot);
            final MapKey key = pair.getKey();
            if (key.getAccountId() > 20) {
                accounts.add(key.getAccountId());
            }
        }

        assertTrue(accounts.size() < 6_000, "Expected on average a size less than 6K but got " + accounts.size());
    }
}
