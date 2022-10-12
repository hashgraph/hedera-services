/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.migration;

import static org.apache.tuweni.units.bigints.UInt256.ZERO;

import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.virtualmap.VirtualMap;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class KvPairIterationMigrator
        implements InterruptableConsumer<Pair<ContractKey, ContractValue>> {
    private static final Logger log = LogManager.getLogger(KvPairIterationMigrator.class);

    private static final ContractValue ZERO_VALUE = ContractValue.from(ZERO);

    private final int insertionsPerCopy;
    private final SortedSet<EntityNum> presentContractNums = new TreeSet<>();
    private final Map<EntityNum, ContractKey> rootKeys = new HashMap<>();
    private final Map<EntityNum, Integer> numNonZeroKvPairs = new HashMap<>();
    private final AccountStorageAdapter contracts;
    private final SizeLimitedStorage.IterableStorageUpserter storageUpserter;

    private int numInsertions = 0;
    private int numSkipped = 0;
    private VirtualMap<ContractKey, IterableContractValue> iterableContractStorage;

    public KvPairIterationMigrator(
            final int insertionsPerCopy,
            final AccountStorageAdapter contracts,
            final SizeLimitedStorage.IterableStorageUpserter storageUpserter,
            final VirtualMap<ContractKey, IterableContractValue> iterableContractStorage) {
        this.contracts = contracts;
        this.insertionsPerCopy = insertionsPerCopy;
        this.storageUpserter = storageUpserter;
        this.iterableContractStorage = iterableContractStorage;
    }

    @Override
    public void accept(final Pair<ContractKey, ContractValue> kvPair) throws InterruptedException {
        final var key = kvPair.getKey();
        final var contractNum = EntityNum.fromLong(key.getContractId());
        if (!contracts.containsKey(contractNum)) {
            log.warn(
                    "Skipping K/V pair ({}, {}) for missing contract 0.0.{}",
                    kvPair.getKey(),
                    kvPair.getValue(),
                    contractNum.longValue());
            return;
        }
        presentContractNums.add(contractNum);
        final var nonIterableValue = kvPair.getValue();
        if (ZERO_VALUE.equals(nonIterableValue)) {
            numSkipped++;
            return;
        }
        numNonZeroKvPairs.merge(contractNum, 1, Integer::sum);
        var rootKey = rootKeys.get(contractNum);
        final var iterableValue = IterableContractValue.from(nonIterableValue.asUInt256());
        rootKey =
                storageUpserter.upsertMapping(
                        key, iterableValue, rootKey, null, iterableContractStorage);
        numInsertions++;
        if (numInsertions % insertionsPerCopy == 0) {
            final var copy = iterableContractStorage.copy();
            log.info(
                    "After {} insertions, the iterable storage map had root hash {}",
                    numInsertions,
                    iterableContractStorage.getRight().getHash());
            iterableContractStorage.release();
            iterableContractStorage = copy;
        }
        rootKeys.put(contractNum, rootKey);
    }

    public VirtualMap<ContractKey, IterableContractValue> getMigratedStorage() {
        return iterableContractStorage;
    }

    public void finish() {
        presentContractNums.forEach(
                contractNum -> {
                    final var contract = contracts.getForModify(contractNum);
                    contract.setNumContractKvPairs(numNonZeroKvPairs.getOrDefault(contractNum, 0));
                    final var rootKey = rootKeys.get(contractNum);
                    if (rootKey != null) {
                        contract.setFirstUint256StorageKey(rootKey.getKey());
                    }
                    log.debug(
                            "Migrated {} k/v pairs from contract {}",
                            contract.getNumContractKvPairs(),
                            contractNum.toIdString());
                });

        log.info(
                "Migration summary: {} k/v pairs migrated. {} skipped.", numInsertions, numSkipped);
    }
}
