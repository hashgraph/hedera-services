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
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractStorageListMutation;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.MapAccessType;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MapValueListUtils;
import com.swirlds.virtualmap.VirtualMap;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ContractGC {
    private static final Logger log = LogManager.getLogger(ContractGC.class);
    static final List<MapAccessType> BYTECODE_REMOVAL_WORK = List.of(BLOBS_REMOVE);
    static final List<MapAccessType> ROOT_KEY_UPDATE_WORK = List.of(ACCOUNTS_GET_FOR_MODIFY);
    static final List<MapAccessType> ONLY_SLOT_REMOVAL_WORK = List.of(STORAGE_REMOVE);
    static final List<MapAccessType> NEXT_SLOT_REMOVAL_WORK =
            List.of(STORAGE_REMOVE, STORAGE_GET, STORAGE_GET, STORAGE_PUT);

    private final ExpiryThrottle expiryThrottle;
    private final Supplier<AccountStorageAdapter> contracts;
    private final Supplier<VirtualMap<ContractKey, IterableContractValue>> storage;
    private final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode;

    private ContractGC.RemovalFacilitation removalFacilitation =
            MapValueListUtils::removeFromMapValueList;

    @Inject
    public ContractGC(
            final ExpiryThrottle expiryThrottle,
            final Supplier<AccountStorageAdapter> contracts,
            final Supplier<VirtualMap<ContractKey, IterableContractValue>> storage,
            final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode) {
        this.expiryThrottle = expiryThrottle;
        this.contracts = contracts;
        this.storage = storage;
        this.bytecode = bytecode;
    }

    public boolean expireBestEffort(
            final EntityNum expiredContractNum, final HederaAccount contract) {
        final var numKvPairs = contract.getNumContractKvPairs();
        var isDeleted = contract.isDeleted();
        if (numKvPairs > 0) {
            if (!expiryThrottle.allow(ROOT_KEY_UPDATE_WORK)) {
                return false;
            }
            final var slotRemovals =
                    removeKvPairs(
                            numKvPairs,
                            expiredContractNum,
                            contract.getFirstContractStorageKey(),
                            storage.get());
            final var numRemoved = slotRemovals.numRemoved();
            if (numRemoved == 0) {
                expiryThrottle.reclaimLastAllowedUse();
                return false;
            } else {
                final var mutableContract = contracts.get().getForModify(expiredContractNum);
                mutableContract.setNumContractKvPairs(numKvPairs - numRemoved);
                // Once we've done any auto-removal work, we make sure the contract is deleted
                mutableContract.setDeleted(true);
                isDeleted = true;
                if (slotRemovals.newRoot() != null) {
                    mutableContract.setFirstUint256StorageKey(slotRemovals.newRoot().getKey());
                    return false;
                }
            }
        }
        return tryToRemoveBytecode(expiredContractNum, isDeleted);
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
            try {
                contractKey = removalFacilitation.removeNext(contractKey, contractKey, listRemoval);
                n++;
            } catch (Exception unrecoverable) {
                log.error(
                        "Unable to reclaim all storage from contract 0.0.{}",
                        contractNum,
                        unrecoverable);
                contractKey = null;
            }
        }
        if (contractKey == null) {
            // Treat all pairs as removed if we have no more non-null keys
            n = maxKvPairs;
        }
        return new SlotRemovalOutcome(n, contractKey);
    }

    private List<MapAccessType> workToRemoveFrom(final int remainingPairs) {
        return remainingPairs == 1 ? ONLY_SLOT_REMOVAL_WORK : NEXT_SLOT_REMOVAL_WORK;
    }

    private boolean tryToRemoveBytecode(
            final EntityNum expiredContractNum, final boolean alreadyDeleted) {
        if (!alreadyDeleted) {
            if (!expiryThrottle.allow(ROOT_KEY_UPDATE_WORK)) {
                return false;
            } else {
                final var mutableContract = contracts.get().getForModify(expiredContractNum);
                // Make sure the contract is deleted before potentially removing its bytecode below
                mutableContract.setDeleted(true);
            }
        }
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
