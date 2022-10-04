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
package com.hedera.services.state.tasks;

import static com.hedera.services.state.tasks.SystemTaskResult.*;
import static com.hedera.services.throttling.MapAccessType.*;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.store.contracts.EntityAccess;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.StorageChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.SidecarUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link SystemTask} added in release 0.31 that exports the bytecode and storage slots of all
 * contracts from the 0.30.x saved state. After all pre-existing entities have been scanned, always
 * returns false from {@code isActive()}.
 */
@Singleton
public class TraceabilityExportTask implements SystemTask {
    private static final Logger log = LogManager.getLogger(TraceabilityExportTask.class);

    private final EntityAccess entityAccess;
    private final ExpiryThrottle expiryThrottle;
    private final GlobalDynamicProperties dynamicProperties;
    private final TraceabilityRecordsHelper recordsHelper;
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
    private final Supplier<VirtualMap<ContractKey, IterableContractValue>> contractStorage;

    @Inject
    public TraceabilityExportTask(
            final EntityAccess entityAccess,
            final ExpiryThrottle expiryThrottle,
            final GlobalDynamicProperties dynamicProperties,
            final TraceabilityRecordsHelper recordsHelper,
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
            final Supplier<VirtualMap<ContractKey, IterableContractValue>> contractStorage) {
        this.entityAccess = entityAccess;
        this.expiryThrottle = expiryThrottle;
        this.dynamicProperties = dynamicProperties;
        this.accounts = accounts;
        this.recordsHelper = recordsHelper;
        this.contractStorage = contractStorage;
    }

    @Override
    public boolean isActive(final long literalNum, final MerkleNetworkContext curNetworkCtx) {
        return dynamicProperties.shouldDoTraceabilityExport()
                && !curNetworkCtx.areAllPreUpgradeEntitiesScanned()
                // No need to do traceability export for an 0.31.x contract
                && literalNum < curNetworkCtx.seqNoPostUpgrade();
    }

    @Override
    public SystemTaskResult process(final long literalNum, final Instant now) {
        if (!recordsHelper.canExportNow()) {
            return NEEDS_DIFFERENT_CONTEXT;
        }
        // It would be a lot of work to split even a single sidecar's construction across
        // multiple process() calls, so we just unconditionally register work in the
        // throttle bucket; will only happen once per pre-existing contract
        expiryThrottle.allowOne(ACCOUNTS_GET);

        final var key = EntityNum.fromLong(literalNum);
        final var account = accounts.get().get(key);
        if (account == null || !account.isSmartContract()) {
            return NOTHING_TO_DO;
        }
        final var contractId = key.toGrpcContractID();
        final List<TransactionSidecarRecord.Builder> sidecars = new ArrayList<>();
        addBytecodeSidecar(contractId, sidecars);
        // We ignore contracts that don't have bytecode
        if (!sidecars.isEmpty()) {
            addStateChangesSideCar(contractId, account, sidecars);
            recordsHelper.exportSidecarsViaSynthUpdate(literalNum, sidecars);
        }
        return DONE;
    }

    private void addStateChangesSideCar(
            final ContractID contractId,
            final MerkleAccount contract,
            final List<TransactionSidecarRecord.Builder> sidecars) {
        var contractStorageKey = contract.getFirstContractStorageKey();
        if (contractStorageKey == null) {
            return;
        }
        final var stateChangesSidecar =
                generateMigrationStateChangesSidecar(
                        contractId, contractStorageKey, contract.getNumContractKvPairs());
        sidecars.add(stateChangesSidecar);
    }

    private void addBytecodeSidecar(
            final ContractID contractId, final List<TransactionSidecarRecord.Builder> sidecars) {
        final var bytecodeSidecar = generateMigrationBytecodeSidecarFor(contractId);
        if (bytecodeSidecar == null) {
            log.warn(
                    "Contract 0.0.{} has no bytecode in state - no migration"
                            + " sidecar records will be published.",
                    contractId.getContractNum());
        } else {
            sidecars.add(bytecodeSidecar);
        }
    }

    private TransactionSidecarRecord.Builder generateMigrationBytecodeSidecarFor(
            final ContractID contractId) {
        expiryThrottle.allowOne(BLOBS_GET);
        final var runtimeCode =
                entityAccess.fetchCodeIfPresent(EntityIdUtils.asAccount(contractId));
        if (runtimeCode == null) {
            return null;
        }
        final var bytecodeSidecar =
                SidecarUtils.createContractBytecodeSidecarFrom(
                        contractId, runtimeCode.toArrayUnsafe());
        bytecodeSidecar.setMigration(true);
        return bytecodeSidecar;
    }

    private TransactionSidecarRecord.Builder generateMigrationStateChangesSidecar(
            final ContractID contractId,
            ContractKey contractStorageKey,
            int maxNumberOfKvPairsToIterate) {
        final var contractStateChangeBuilder =
                ContractStateChange.newBuilder().setContractId(contractId);

        IterableContractValue iterableValue;
        final var curStorage = contractStorage.get();
        while (maxNumberOfKvPairsToIterate > 0 && contractStorageKey != null) {
            expiryThrottle.allowOne(STORAGE_GET);
            iterableValue = curStorage.get(contractStorageKey);
            contractStateChangeBuilder.addStorageChanges(
                    StorageChange.newBuilder()
                            .setSlot(ByteStringUtils.wrapUnsafely(slotAsBytes(contractStorageKey)))
                            .setValueRead(
                                    ByteStringUtils.wrapUnsafely(
                                            iterableValue
                                                    .asUInt256()
                                                    .trimLeadingZeros()
                                                    .toArrayUnsafe()))
                            .build());
            contractStorageKey =
                    iterableValue.getNextKeyScopedTo(contractStorageKey.getContractId());
            maxNumberOfKvPairsToIterate--;
        }

        if (maxNumberOfKvPairsToIterate != 0) {
            log.warn(
                    "After walking through all iterable storage of contract 0.0.{},"
                        + " numContractKvPairs field indicates that there should have been {} more"
                        + " k/v pair(s) left",
                    contractId.getContractNum(),
                    maxNumberOfKvPairsToIterate);
        }

        return TransactionSidecarRecord.newBuilder()
                .setStateChanges(
                        ContractStateChanges.newBuilder()
                                .addContractStateChanges(contractStateChangeBuilder)
                                .build())
                .setMigration(true);
    }

    private byte[] slotAsBytes(final ContractKey contractStorageKey) {
        final var numOfNonZeroBytes = contractStorageKey.getUint256KeyNonZeroBytes();
        // getUint256KeyNonZeroBytes() returns 1 even if slot is 0, so
        // check the least significant int in the int[] representation
        // of the key to make sure we are in the edge case
        if (numOfNonZeroBytes == 1 && contractStorageKey.getKey()[7] == 0) {
            return new byte[0];
        }
        final var contractKeyBytes = new byte[numOfNonZeroBytes];
        for (int i = numOfNonZeroBytes - 1, j = numOfNonZeroBytes - i - 1; i >= 0; i--, j++) {
            contractKeyBytes[j] = contractStorageKey.getUint256Byte(i);
        }
        return contractKeyBytes;
    }
}
