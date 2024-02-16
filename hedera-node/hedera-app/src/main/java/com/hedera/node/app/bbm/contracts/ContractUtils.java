/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.bbm.contracts;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.node.app.bbm.accounts.HederaAccount;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.state.InitialModServiceContractSchema;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey.Type;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.utils.NonAtomicReference;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.merkle.memory.InMemoryKey;
import com.hedera.node.app.state.merkle.memory.InMemoryValue;
import com.hedera.node.app.state.merkle.memory.InMemoryWritableKVState;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class ContractUtils {

    private static final SemanticVersion CURRENT_VERSION = new SemanticVersion(0, 47, 0, "SNAPSHOT", "");

    static final int ESTIMATED_NUMBER_OF_CONTRACTS = 100_000;
    static final int ESTIMATED_NUMBER_OF_DELETED_CONTRACTS = 10_000;

    private ContractUtils() {
        // Utility class
    }

    /**
     * Return all the bytecodes for all the contracts in this state.
     */
    @NonNull
    public static Contracts getMonoContracts(
            VirtualMapLike<VirtualBlobKey, VirtualBlobValue> files, AccountStorageAdapter accountAdapter) {
        final var contractIds = getAllKnownContracts(accountAdapter);
        final var deletedContractIds = getAllDeletedContracts(accountAdapter);
        final var contractContents = getAllContractContents(files, contractIds, deletedContractIds);
        return new Contracts(contractContents, deletedContractIds, contractIds.size());
    }

    /**
     * Returns all contracts known via Hedera accounts, by their contract id (lowered to an Integer)
     */
    @NonNull
    private static Set</*@NonNull*/ Integer> getAllKnownContracts(AccountStorageAdapter accounts) {
        final var ids = new HashSet<Integer>(ESTIMATED_NUMBER_OF_CONTRACTS);
        accounts.forEach((k, v) -> {
            if (null != k && null != v && v.isSmartContract()) {
                ids.add(k.intValue());
            }
        });
        return ids;
    }

    /** Returns the ids of all deleted contracts ("self-destructed") */
    @NonNull
    private static Set</*@NonNull*/ Integer> getAllDeletedContracts(AccountStorageAdapter accounts) {
        final var ids = new HashSet<Integer>(ESTIMATED_NUMBER_OF_DELETED_CONTRACTS);
        accounts.forEach((k, v) -> {
            if (null != k && null != v && v.isSmartContract() && v.isDeleted()) {
                ids.add(k.intValue());
            }
        });
        return ids;
    }

    /** Returns the bytecodes for all the requested contracts */
    @NonNull
    private static Collection</*@NonNull*/ Contract> getAllContractContents(
            @NonNull final VirtualMapLike<VirtualBlobKey, VirtualBlobValue> fileStore,
            @NonNull final Collection</*@NonNull*/ Integer> contractIds,
            @NonNull final Collection</*@NonNull*/ Integer> deletedContractIds) {
        Objects.requireNonNull(contractIds);
        Objects.requireNonNull(deletedContractIds);

        final var codes = new ArrayList<Contract>(ESTIMATED_NUMBER_OF_CONTRACTS);
        for (final var cid : contractIds) {
            final var vbk = new VirtualBlobKey(Type.CONTRACT_BYTECODE, cid);
            if (fileStore.containsKey(vbk)) {
                final var blob = fileStore.get(vbk);
                if (null != blob) {
                    final var c = new Contract(
                            new TreeSet<>(),
                            blob.getData(),
                            deletedContractIds.contains(cid) ? Validity.DELETED : Validity.ACTIVE);
                    c.ids().add(cid);
                    codes.add(c);
                }
            }
        }
        return codes;
    }

    static Contracts getModContracts(HederaAccount[] dumpableAccounts) {
        final var smartContracts = Arrays.stream(dumpableAccounts)
                .filter(HederaAccount::smartContract)
                .toList();
        final var deletedSmartContract =
                smartContracts.stream().filter(HederaAccount::deleted).toList();

        final var extractedFiles = getContractStore();

        final var contractContents = new ArrayList<Contract>(ESTIMATED_NUMBER_OF_CONTRACTS);
        for (final var smartContract : smartContracts) {
            final var contractId = getContractId(smartContract);
            final var fileId = ContractID.newBuilder().contractNum(contractId).build();
            if (extractedFiles.contains(fileId)) {
                final var bytecode = extractedFiles.get(fileId);
                if (null != bytecode) {
                    final var c = new Contract(
                            new TreeSet<>(),
                            bytecode.code().toByteArray(),
                            deletedSmartContract.contains(smartContract) ? Validity.DELETED : Validity.ACTIVE);
                    c.ids().add(contractId);
                    contractContents.add(c);
                }
            }
        }

        final var deletedContractIds =
                deletedSmartContract.stream().map(ContractUtils::getContractId).toList();
        return new Contracts(contractContents, deletedContractIds, smartContracts.size());
    }

    private static int getContractId(HederaAccount contract) {
        if (contract.accountId() == null || contract.accountId().accountNum() == null) {
            return 0;
        }
        return contract.accountId().accountNum().intValue();
    }

    private static ReadableKVState<ContractID, Bytecode> getContractStore() {
        final var contractSchema = new InitialModServiceContractSchema(CURRENT_VERSION);
        final var contractSchemas = contractSchema.statesToCreate();
        final StateDefinition<ContractID, Bytecode> contractStoreStateDefinition = contractSchemas.stream()
                .filter(sd -> sd.stateKey().equals(InitialModServiceContractSchema.BYTECODE_KEY))
                .findFirst()
                .orElseThrow();
        final var contractStoreSchemaMetadata =
                new StateMetadata<>(ContractService.NAME, contractSchema, contractStoreStateDefinition);
        final var contractMerkleMap = new NonAtomicReference<
                MerkleMap<InMemoryKey<ContractID>, InMemoryValue<ContractID, Bytecode>>>(new MerkleMap<>());
        final var toStore = new NonAtomicReference<ReadableKVState<ContractID, Bytecode>>(
                new InMemoryWritableKVState<>(contractStoreSchemaMetadata, contractMerkleMap.get()));
        return toStore.get();
    }
}
