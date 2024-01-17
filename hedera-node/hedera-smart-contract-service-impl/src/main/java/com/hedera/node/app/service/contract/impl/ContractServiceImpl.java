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
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Supplier;

/**
 * Implementation of the {@link ContractService}.
 */
public enum ContractServiceImpl implements ContractService {
    CONTRACT_SERVICE;
    public static final long INTRINSIC_GAS_LOWER_BOUND = 21_000L;
    private final ContractServiceComponent component;

    // For migrating contract storage:
    private static ContractStateMigrator.StateFlusher flusher;
    private static VirtualMapLike<ContractKey, IterableContractValue> fromState;
    private static WritableKVState<SlotKey, SlotValue> toState;

    // For migrating contract bytecode:
    private static Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss;

    ContractServiceImpl() {
        this.component = DaggerContractServiceComponent.create();
    }

    public static void setFileFs(Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss) {
        ContractServiceImpl.fss = fss;
    }

    public static void setFlusher(ContractStateMigrator.StateFlusher flusher) {
        ContractServiceImpl.flusher = flusher;
    }

    public static void setFromState(VirtualMapLike<ContractKey, IterableContractValue> fromState) {
        ContractServiceImpl.fromState = fromState;
    }

    public static void setToState(WritableKVState<SlotKey, SlotValue> toState) {
        ContractServiceImpl.toState = toState;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, final SemanticVersion version) {
        // We intentionally ignore the given (i.e. passed-in) version in this method
        registry.register(new InitialModServiceContractSchema(version));
    }

    public ContractHandlers handlers() {
        return component.handlers();
    }

    public static ContractStateMigrator.StateFlusher getFlusher() {
        return flusher;
    }

    public static VirtualMapLike<ContractKey, IterableContractValue> getFromState() {
        return fromState;
    }

    public static WritableKVState<SlotKey, SlotValue> getToState() {
        return toState;
    }

    public static Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> getFss() {
        return fss;
    }
}
