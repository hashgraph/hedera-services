/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.mono.state.migration.ContractStateMigrator;
import com.hedera.node.app.service.contract.impl.handlers.ContractHandlers;
import com.hedera.node.app.service.contract.impl.state.ContractSchema;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.WritableKVState;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation of the {@link ContractService}.
 */
public enum ContractServiceImpl implements ContractService {
    CONTRACT_SERVICE;
    public static final long INTRINSIC_GAS_LOWER_BOUND = 21_000L;
    private final ContractServiceComponent component;
    private ContractStateMigrator.StateFlusher flusher;
    private VirtualMapLike<ContractKey, IterableContractValue> fromState;
    private WritableKVState<SlotKey, SlotValue> toState;

    ContractServiceImpl() {
        this.component = DaggerContractServiceComponent.create();
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
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        var cs = new ContractSchema();
        registry.register(cs);

//        if(true)return;
        registry.register(new Schema(SemanticVersion.newBuilder().minor(45).build()) {

           @Override
           public void migrate(MigrationContext ctx) {
               System.out.println("BBM:hey from contract service bbm");

               // currently works: 1dec23 @ 11:36
               var result = ContractStateMigrator.migrateFromContractStorageVirtualMap(fromState, toState, flusher);

               fromState = null;
               toState = null;

               System.out.println("BBM:migrate result: " + result);
           }
        });
    }

    public ContractHandlers handlers() {
        return component.handlers();
    }
}
