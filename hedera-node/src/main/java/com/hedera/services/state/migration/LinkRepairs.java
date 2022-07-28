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

import static com.hedera.services.state.virtual.KeyPackingUtils.readableContractStorageKey;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Accepts key/value pairs from up to two full scans of the storage {@code VirtualMap}, building up
 * the internal state needed to repair any corruptions in the key/value linked lists. Then performs
 * any needed repairs via {@link LinkRepairs#fixAnyBrokenLinks()}.
 *
 * <p>During the first scan, checks each {@link IterableContractValue} for corruption in the form
 * of:
 *
 * <ol>
 *   <li>A {@code next} pointer to a non-existent key; or, <i>if</i> the value is for a non-root
 *       key,
 *   <li>An unset {@code prev} pointer.
 * </ol>
 *
 * Every contract that has a corrupt link is added to the {@code keyListsToRepair} map.
 *
 * <p>If there are key lists to repair after the first scan, then during the second scan, adds
 * <i>all keys</i> for each corrupted contract to a contract-scoped list. When {@link
 * LinkRepairs#fixAnyBrokenLinks()} is called, iterates through each list to repair the
 * corresponding {@link IterableContractValue} objects with valid {@code prev}/{@code next}
 * pointers, calling {@link MerkleAccount#setFirstUint256StorageKey(int[])} with the new root key.
 */
public class LinkRepairs
        implements InterruptableConsumer<Pair<ContractKey, IterableContractValue>> {
    private static final Logger log = LogManager.getLogger(LinkRepairs.class);

    private final Map<EntityNum, List<ContractKey>> keyListsToRepair = new TreeMap<>();
    private final MerkleMap<EntityNum, MerkleAccount> contracts;
    private final VirtualMap<ContractKey, IterableContractValue> storage;

    private boolean isSecondScan = false;

    public LinkRepairs(
            final MerkleMap<EntityNum, MerkleAccount> contracts,
            final VirtualMap<ContractKey, IterableContractValue> storage) {
        this.contracts = contracts;
        this.storage = storage;
    }

    @Override
    public void accept(final Pair<ContractKey, IterableContractValue> kvPair)
            throws InterruptedException {
        final var key = kvPair.getKey();
        final var explicitKey = key.getKey();
        final var contractNum = EntityNum.fromLong(key.getContractId());
        if (isSecondScan) {
            final var keysToRepair = keyListsToRepair.get(contractNum);
            if (keysToRepair != null) {
                keysToRepair.add(key);
            }
        } else if (hasBrokenLink(contractNum, explicitKey, kvPair.getValue())) {
            keyListsToRepair.computeIfAbsent(contractNum, ignore -> new ArrayList<>());
        }
    }

    public void markScanComplete() {
        isSecondScan = true;
    }

    public boolean hasBrokenLinks() {
        return !keyListsToRepair.isEmpty();
    }

    public void fixAnyBrokenLinks() {
        keyListsToRepair.forEach(
                (num, keyList) -> {
                    final var contract = contracts.getForModify(num);
                    contract.setFirstUint256StorageKey(keyList.get(0).getKey());
                    final var n = keyList.size();
                    for (int i = 0; i < n; i++) {
                        final var key = keyList.get(i);
                        final var value = storage.get(key).copy();
                        if (i == 0) {
                            value.markAsRootMapping();
                        } else {
                            value.setPrevKey(keyList.get(i - 1).getKey());
                        }
                        if (i == n - 1) {
                            value.markAsLastMapping();
                        } else {
                            value.setNextKey(keyList.get(i + 1).getKey());
                        }
                        storage.put(key, value);
                    }
                    contract.setNumContractKvPairs(n);
                });
    }

    private boolean hasBrokenLink(
            final EntityNum contractNum,
            final int[] explicitKey,
            final IterableContractValue value) {
        final var contract = contracts.get(contractNum);
        if (contract == null) {
            log.warn(
                    "Skipping K/V pair ({}, {}) for missing contract 0.0.{}",
                    () -> readableContractStorageKey(explicitKey),
                    () -> value,
                    contractNum::longValue);
            return false;
        }

        final var explicitPrevKey = value.getExplicitPrevKey();
        if (explicitPrevKey == null && !Arrays.equals(explicitKey, contract.getFirstUint256Key())) {
            log.warn(
                    "0.0.{} root key is {}, but {} had an unset prev pointer",
                    contractNum::longValue,
                    () -> readableContractStorageKey(contract.getFirstUint256Key()),
                    () -> readableContractStorageKey(explicitKey));
            return true;
        }
        final var nextKey = value.getNextKeyScopedTo(contractNum.longValue());
        if (nextKey != null && !storage.containsKey(nextKey)) {
            log.warn(
                    "0.0.{} included key {} with missing next pointer {}",
                    contractNum::longValue,
                    () -> readableContractStorageKey(explicitKey),
                    () -> readableContractStorageKey(nextKey.getKey()));
            return true;
        }
        return false;
    }
}
