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
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.mono.state.codec.MonoMapCodecAdapter;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.ContractKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link ContractService} {@link com.hedera.node.app.spi.Service}.
 */
public final class ContractServiceImpl implements ContractService {
    private static final int MAX_STORAGE_ENTRIES = 4096;
    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().minor(34).build();
    public static final String STORAGE_KEY = "STORAGE";

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        registry.register(contractSchema());
    }

    private Schema contractSchema() {
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            @SuppressWarnings("rawtypes")
            public Set<StateDefinition> statesToCreate() {
                return Set.of(storageDef());
            }
        };
    }

    private static StateDefinition<ContractKey, IterableContractValue> storageDef() {
        final var keySerdes = MonoMapCodecAdapter.codecForVirtualKey(
                ContractKey.MERKLE_VERSION, ContractKey::new, new ContractKeySerializer());
        final var valueSerdes = MonoMapCodecAdapter.codecForVirtualValue(
                IterableContractValue.ITERABLE_VERSION, IterableContractValue::new);

        return StateDefinition.onDisk(STORAGE_KEY, keySerdes, valueSerdes, MAX_STORAGE_ENTRIES);
    }
}
