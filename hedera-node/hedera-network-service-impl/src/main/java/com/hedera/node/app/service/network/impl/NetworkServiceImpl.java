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
package com.hedera.node.app.service.network.impl;

import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.network.impl.serdes.EntityNumSerdes;
import com.hedera.node.app.service.network.impl.serdes.MonoBookAdapterSerdes;
import com.hedera.node.app.service.network.impl.serdes.MonoContextAdapterSerdes;
import com.hedera.node.app.service.network.impl.serdes.MonoRunningHashesAdapterSerdes;
import com.hedera.node.app.service.network.impl.serdes.MonoSpecialFilesAdapterSerdes;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapSerdesAdapter;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Set;

/**
 * Standard implementation of the {@link NetworkService} {@link com.hedera.node.app.spi.Service}.
 */
public final class NetworkServiceImpl implements NetworkService {
    private static final String CONTEXT_KEY = "CONTEXT";
    private static final String STAKING_KEY = "STAKING";
    private static final String ADDRESS_BOOK_KEY = "ADDRESS_BOOK";
    private static final String SPECIAL_FILES_KEY = "SPECIAL_FILES";
    private static final String RUNNING_HASHES_KEY = "RUNNING_HASHES";
    private static final SemanticVersion CURRENT_VERSION = SemanticVersion.newBuilder()
            .setMinor(34).build();

    @Override
    public void registerSchemas(final @NonNull SchemaRegistry registry) {
        registry.register(networkSchema());
    }

    private Schema networkSchema() {
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        stakingDef(),
                        StateDefinition.singleton(CONTEXT_KEY, new MonoContextAdapterSerdes()),
                        StateDefinition.singleton(ADDRESS_BOOK_KEY, new MonoBookAdapterSerdes()),
                        StateDefinition.singleton(SPECIAL_FILES_KEY, new MonoSpecialFilesAdapterSerdes()),
                        StateDefinition.singleton(RUNNING_HASHES_KEY, new MonoRunningHashesAdapterSerdes()));
            }
        };
    }

    private StateDefinition<EntityNum, MerkleStakingInfo> stakingDef() {
        final var keySerdes = new EntityNumSerdes();
        final var valueSerdes = MonoMapSerdesAdapter.serdesForSelfSerializable(
                MerkleStakingInfo.CURRENT_VERSION, MerkleStakingInfo::new);
        return StateDefinition.inMemory(STAKING_KEY, keySerdes, valueSerdes);
    }
}
