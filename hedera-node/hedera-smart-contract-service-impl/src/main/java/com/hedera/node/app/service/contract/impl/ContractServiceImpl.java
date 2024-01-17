/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.handlers.ContractHandlers;
import com.hedera.node.app.service.contract.impl.state.InitialModServiceContractSchema;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.migration.ContractStateMigrator;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Implementation of the {@link ContractService}.
 */
public enum ContractServiceImpl implements ContractService {
    CONTRACT_SERVICE;
    public static final long INTRINSIC_GAS_LOWER_BOUND = 21_000L;
    private final ContractServiceComponent component;

    // For migrating contract storage:
    private ContractStateMigrator.StateFlusher flusher;
    private VirtualMapLike<ContractKey, IterableContractValue> fromState;
    private WritableKVState<SlotKey, SlotValue> toState;

    // For migrating contract bytecode:
    private Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss;

    ContractServiceImpl() {
        this.component = DaggerContractServiceComponent.create();
    }

    public void setFileFs(Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss) {
        this.fss = fss;
    }

    public void setFlusher(ContractStateMigrator.StateFlusher flusher) {
        this.flusher = flusher;
    }

    public void setFromState(VirtualMapLike<ContractKey, IterableContractValue> fromState) {
        this.fromState = fromState;
    }

    public void setToState(WritableKVState<SlotKey, SlotValue> toState) {
        this.toState = toState;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, final SemanticVersion version) {
        // We intentionally ignore the given (i.e. passed-in) version in this method
        registry.register(new InitialModServiceContractSchema(RELEASE_045_VERSION));

        registry.register(new Schema(RELEASE_MIGRATION_VERSION) {
            @Override
            public void migrate(@NonNull final MigrationContext ctx) {
                if (fromState != null) {
                    System.out.println("BBM: migrating contract service");

                    System.out.println("BBM: migrating contract k/v storage...");
                    var result =
                            ContractStateMigrator.migrateFromContractStorageVirtualMap(fromState, toState, flusher);
                    fromState = null;
                    toState = null;
                    System.out.println("BBM: finished migrating contract storage. Result: " + result);

                    System.out.println("BBM: migrating contract bytecode...");
                    WritableKVState<EntityNumber, Bytecode> bytecodeTs =
                            ctx.newStates().get(InitialModServiceContractSchema.BYTECODE_KEY);
                    var migratedContractNums = new ArrayList<Integer>();
                    try {
                        fss.get()
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

                    System.out.println(
                            "BBM: finished migrating contract bytecode. Contract nums: " + migratedContractNums);

                    if (bytecodeTs.isModified()) ((WritableKVStateBase) bytecodeTs).commit();

                    fss = null;

                    System.out.println("BBM: contract migration finished");
                }
            }
        });
    }

    public ContractHandlers handlers() {
        return component.handlers();
    }
}
