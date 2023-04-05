/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.migration;

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.virtualmap.VirtualMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Accepts key/value pairs from a full scans of the storage {@code VirtualMap}, building up
 * an in-memory picture of its entire contents. Then re-creates the storage map and links
 * using the stable {@link ContractKey#hashCode()} implementation.
 */
public class LinkRepairs implements InterruptableConsumer<Pair<ContractKey, IterableContractValue>> {
    private final Map<EntityNum, List<Pair<ContractKey, IterableContractValue>>> mappingsToRepair = new TreeMap<>();
    private final MerkleMapLike<EntityNum, MerkleAccount> contracts;
    private final VirtualMap<ContractKey, IterableContractValue> storage;
    private final List<ContractKey> allKeys = new ArrayList<>();

    public LinkRepairs(
            final MerkleMapLike<EntityNum, MerkleAccount> contracts,
            final VirtualMap<ContractKey, IterableContractValue> storage) {
        this.contracts = contracts;
        this.storage = storage;
    }

    @Override
    public void accept(final Pair<ContractKey, IterableContractValue> kvPair) throws InterruptedException {
        final var key = kvPair.getKey();
        allKeys.add(key);
        final var contractNum = EntityNum.fromLong(key.getContractId());
        mappingsToRepair
                .computeIfAbsent(contractNum, ignore -> new ArrayList<>())
                .add(kvPair);
    }

    public void rebuildStorageMapAndLinks() {
        // First remove any keys reachable using the undesired hashCode()
        ContractKey.setUseStableHashCode(false);
        allKeys.forEach(this::tryToRemove);
        // Now switch to purely using
        ContractKey.setUseStableHashCode(true);

        mappingsToRepair.forEach((num, mappingList) -> {
            final var contract = contracts.getForModify(num);
            // We can only have a contract number here if it has non-empty storage
            final var firstMapping = mappingList.get(0);
            contract.setFirstUint256StorageKey(firstMapping.getLeft().getKey());
            final var n = mappingList.size();
            for (int i = 0; i < n; i++) {
                final var mapping = mappingList.get(i);
                // Copy to make sure this is mutable
                final var value = mapping.getValue().copy();
                if (i == 0) {
                    value.markAsRootMapping();
                } else {
                    final var prevMapping = mappingList.get(i - 1);
                    value.setPrevKey(prevMapping.getLeft().getKey());
                }
                if (i == n - 1) {
                    value.markAsLastMapping();
                } else {
                    final var nextMapping = mappingList.get(i + 1);
                    value.setNextKey(nextMapping.getLeft().getKey());
                }
                storage.put(mapping.getKey(), value);
            }
            contract.setNumContractKvPairs(n);
        });
    }

    private void tryToRemove(final ContractKey key) {
        try {
            storage.remove(key);
        } catch (final IllegalArgumentException | NullPointerException ignore) {
            // Ignore any exceptions like:
            //  - IllegalArgumentException: path (X) is not valid; must be in range (Y,Z)
            //  - NullPointerException: Cannot invoke "VirtualLeafRecord.setPath(long)" because "lastLeaf" is null
        }
    }
}
