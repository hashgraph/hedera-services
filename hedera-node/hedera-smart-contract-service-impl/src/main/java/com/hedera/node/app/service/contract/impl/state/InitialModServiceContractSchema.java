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

package com.hedera.node.app.service.contract.impl.state;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.mono.state.migration.ContractStateMigrator;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Set;

/**
 * Defines the schema for the contract service's state.
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47ContractSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
public class InitialModServiceContractSchema extends Schema {
    public static final String STORAGE_KEY = "STORAGE";
    public static final String BYTECODE_KEY = "BYTECODE";
    private static final int MAX_BYTECODES = 50_000_000;
    private static final int MAX_STORAGE_ENTRIES = 500_000_000;

    public InitialModServiceContractSchema(final SemanticVersion version) {
        super(version);
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        if (ContractServiceImpl.getFromState() != null) {
            System.out.println("BBM: migrating contract service");

            System.out.println("BBM: migrating contract k/v storage...");
            var result = ContractStateMigrator.migrateFromContractStorageVirtualMap(
                    ContractServiceImpl.getFromState(),
                    ContractServiceImpl.getToState(),
                    ContractServiceImpl.getFlusher());
            ContractServiceImpl.setFromState(null);
            ContractServiceImpl.setToState(null);
            System.out.println("BBM: finished migrating contract storage. Result: " + result);

            System.out.println("BBM: migrating contract bytecode...");
            WritableKVState<EntityNumber, Bytecode> bytecodeTs =
                    ctx.newStates().get(InitialModServiceContractSchema.BYTECODE_KEY);
            var migratedContractNums = new ArrayList<Integer>();
            try {
                ContractServiceImpl.getFss()
                        .get()
                        .extractVirtualMapData(
                                AdHocThreadManager.getStaticThreadManager(),
                                entry -> {
                                    if (VirtualBlobKey.Type.CONTRACT_BYTECODE
                                            == entry.left().getType()) {
                                        var contractId = entry.left().getEntityNumCode();
                                        var contents = entry.right().getData();
                                        Bytes wrappedContents;
                                        if (contents == null || contents.length < 1) {
                                            System.out.println(
                                                    "BBM: contract contents null for contractId " + contractId);
                                            wrappedContents = Bytes.EMPTY;
                                        } else {
                                            System.out.println("BBM: migrating contract contents (length "
                                                    + contents.length
                                                    + ") for contractId "
                                                    + contractId);
                                            wrappedContents = Bytes.wrap(contents);
                                        }
                                        bytecodeTs.put(
                                                EntityNumber.newBuilder()
                                                        .number(contractId)
                                                        .build(),
                                                Bytecode.newBuilder()
                                                        .code(wrappedContents)
                                                        .build());
                                        migratedContractNums.add(contractId);
                                    }
                                },
                                1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            System.out.println("BBM: finished migrating contract bytecode. Contract nums: " + migratedContractNums);

            if (bytecodeTs.isModified()) ((WritableKVStateBase) bytecodeTs).commit();

            ContractServiceImpl.setFileFs(null);

            System.out.println("BBM: contract migration finished");
        }
    }

    @NonNull
    @Override
    @SuppressWarnings("rawtypes")
    public Set<StateDefinition> statesToCreate() {
        return Set.of(storageDef(), bytecodeDef());
    }

    private @NonNull StateDefinition<SlotKey, SlotValue> storageDef() {
        return StateDefinition.onDisk(STORAGE_KEY, SlotKey.PROTOBUF, SlotValue.PROTOBUF, MAX_STORAGE_ENTRIES);
    }

    private @NonNull StateDefinition<EntityNumber, Bytecode> bytecodeDef() {
        return StateDefinition.onDisk(BYTECODE_KEY, EntityNumber.PROTOBUF, Bytecode.PROTOBUF, MAX_BYTECODES);
    }
}
