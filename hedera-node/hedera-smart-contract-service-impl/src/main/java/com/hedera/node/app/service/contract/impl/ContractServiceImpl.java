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
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.handlers.ContractHandlers;
import com.hedera.node.app.service.contract.impl.state.InitialModServiceContractSchema;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.state.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Supplier;

/**
 * Implementation of the {@link ContractService}.
 */
public enum ContractServiceImpl implements ContractService {
    CONTRACT_SERVICE;
    public static final long INTRINSIC_GAS_LOWER_BOUND = 21_000L;
    private final ContractServiceComponent component;

    private static VirtualMapLike<ContractKey, IterableContractValue> storageFromState;
    private static Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> bytecodeFromState;

    ContractServiceImpl() {
        this.component = DaggerContractServiceComponent.create();
    }

    public static void setStorageFromState(VirtualMapLike<ContractKey, IterableContractValue> storageFromState) {
        ContractServiceImpl.storageFromState = storageFromState;
    }

    public static void setBytecodeFromState(
            Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> bytecodeFromState) {
        ContractServiceImpl.bytecodeFromState = bytecodeFromState;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, final SemanticVersion version) {
        registry.register(new InitialModServiceContractSchema(version, storageFromState, bytecodeFromState));

        // Once the 'from' state is passed in to the schema class, we don't need that reference in this class anymore.
        // We don't want to keep these references around because, in the case of migrating from mono to mod service, we
        // want the old mono state routes to disappear
        storageFromState = null;
        bytecodeFromState = null;
    }

    public ContractHandlers handlers() {
        return component.handlers();
    }
}
