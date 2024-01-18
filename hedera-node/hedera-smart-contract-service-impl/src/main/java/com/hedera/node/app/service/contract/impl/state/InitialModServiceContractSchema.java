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
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.migration.ContractStateMigrator;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Defines the schema for the contract service's state.
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47ContractSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
public class InitialModServiceContractSchema extends Schema {
    private static final Logger log = LogManager.getLogger(InitialModServiceContractSchema.class);
    public static final String STORAGE_KEY = "STORAGE";
    public static final String BYTECODE_KEY = "BYTECODE";
    private static final int MAX_BYTECODES = 50_000_000;
    private static final int MAX_STORAGE_ENTRIES = 500_000_000;

    // For migrating contract storage:
    private ContractStateMigrator.StateFlusher flusher;
    private VirtualMapLike<ContractKey, IterableContractValue> fromState;
    private WritableKVState<SlotKey, SlotValue> toState;

    // For migrating contract bytecode:
    private Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss;

    public InitialModServiceContractSchema(
            final SemanticVersion version,
            @Nullable final ContractStateMigrator.StateFlusher flusher,
            @Nullable final VirtualMapLike<ContractKey, IterableContractValue> fromState,
            @Nullable final WritableKVState<SlotKey, SlotValue> toState,
            @Nullable final Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss) {
        super(version);
        this.flusher = flusher;
        this.fromState = fromState;
        this.toState = toState;
        this.fss = fss;
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        if (fromState != null) {
            log.info("BBM: migrating contract service");

            log.info("BBM: migrating contract k/v storage...");
            var result = ContractStateMigrator.migrateFromContractStorageVirtualMap(fromState, toState, flusher);
            log.info("BBM: finished migrating contract storage. Result: " + result);

            log.info("BBM: migrating contract bytecode...");
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
                                            log.debug("BBM: contract contents null for contractId " + contractId);
                                            wrappedContents = Bytes.EMPTY;
                                        } else {
                                            log.debug("BBM: migrating contract contents (length "
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

            log.info(
                    "BBM: finished migrating contract bytecode. Number of migrated contracts: " + migratedContractNums);

            if (bytecodeTs.isModified()) ((WritableKVStateBase) bytecodeTs).commit();

            flusher = null;
            fromState = null;
            toState = null;
            fss = null;

            log.info("BBM: contract migration finished");
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
