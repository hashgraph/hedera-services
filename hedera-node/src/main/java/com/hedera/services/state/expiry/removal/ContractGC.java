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
package com.hedera.services.state.expiry.removal;

import static com.hedera.services.state.virtual.VirtualBlobKey.Type.CONTRACT_BYTECODE;
import static com.hedera.services.throttling.MapAccessType.*;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractStorageListMutation;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.MapAccessType;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MapValueListUtils;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ContractGC {
    static final List<MapAccessType> BYTECODE_REMOVAL_WORK = List.of(BLOBS_REMOVE);
    static final List<MapAccessType> ROOT_KEY_UPDATE_WORK = List.of(ACCOUNTS_GET_FOR_MODIFY);
    static final List<MapAccessType> ONLY_SLOT_REMOVAL_WORK = List.of(STORAGE_REMOVE);
    static final List<MapAccessType> NEXT_SLOT_REMOVAL_WORK =
            List.of(STORAGE_REMOVE, STORAGE_GET, STORAGE_PUT);

    private final ExpiryThrottle expiryThrottle;
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts;
    private final Supplier<VirtualMap<ContractKey, IterableContractValue>> storage;
    private final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode;

    private ContractGC.RemovalFacilitation removalFacilitation =
            MapValueListUtils::removeFromMapValueList;

    @Inject
    public ContractGC(
            final ExpiryThrottle expiryThrottle,
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> contracts,
            final Supplier<VirtualMap<ContractKey, IterableContractValue>> storage,
            final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode) {
        this.expiryThrottle = expiryThrottle;
        this.contracts = contracts;
        this.storage = storage;
        this.bytecode = bytecode;
    }

    public boolean expireBestEffort(
            final EntityNum expiredContractNum, final MerkleAccount contract) {
        final var numKvPairs = contract.getNumContractKvPairs();
        boolean rootUpdateThrottled = false;
        if (numKvPairs > 0) {
            rootUpdateThrottled = !expiryThrottle.allow(ROOT_KEY_UPDATE_WORK);
            if (!rootUpdateThrottled) {
                final var slotRemovals =
                        removeKvPairs(
                                numKvPairs,
                                expiredContractNum,
                                contract.getFirstContractStorageKey(),
                                storage.get());
                final var numRemoved = slotRemovals.numRemoved();
                if (numRemoved < numKvPairs) {
                    if (numRemoved == 0) {
                        expiryThrottle.reclaimLastAllowedUse();
                    } else {
                        final var mutableContract =
                                contracts.get().getForModify(expiredContractNum);
                        mutableContract.setNumContractKvPairs(numKvPairs - numRemoved);
                        mutableContract.setFirstUint256StorageKey(slotRemovals.newRoot().getKey());
                    }
                    return false;
                }
            }
        }
        return !rootUpdateThrottled && tryToRemoveBytecode(expiredContractNum);
    }

    private SlotRemovalOutcome removeKvPairs(
            final int maxKvPairs,
            final EntityNum contractNum,
            final ContractKey rootKey,
            final VirtualMap<ContractKey, IterableContractValue> storage) {
        final var listRemoval = new ContractStorageListMutation(contractNum.longValue(), storage);
        var i = maxKvPairs;
        var n = 0;
        var contractKey = rootKey;
        while (contractKey != null && expiryThrottle.allow(workToRemoveFrom(i)) && i-- > 0) {
            // We are always removing the root, hence receiving the new root
            contractKey = removalFacilitation.removeNext(contractKey, contractKey, listRemoval);
            n++;
        }
        return new SlotRemovalOutcome(n, contractKey);
    }

    private List<MapAccessType> workToRemoveFrom(final int remainingPairs) {
        return remainingPairs == 1 ? ONLY_SLOT_REMOVAL_WORK : NEXT_SLOT_REMOVAL_WORK;
    }

    private boolean tryToRemoveBytecode(final EntityNum expiredContractNum) {
        if (!expiryThrottle.allow(BYTECODE_REMOVAL_WORK)) {
            return false;
        }
        final var bytecodeKey =
                new VirtualBlobKey(CONTRACT_BYTECODE, expiredContractNum.intValue());
        final var curBytecode = bytecode.get();
        curBytecode.remove(bytecodeKey);
        return true;
    }

    @FunctionalInterface
    interface RemovalFacilitation {
        ContractKey removeNext(
                ContractKey key, ContractKey root, ContractStorageListMutation listRemoval);
    }

    @VisibleForTesting
    void setRemovalFacilitation(final RemovalFacilitation removalFacilitation) {
        this.removalFacilitation = removalFacilitation;
    }
}
