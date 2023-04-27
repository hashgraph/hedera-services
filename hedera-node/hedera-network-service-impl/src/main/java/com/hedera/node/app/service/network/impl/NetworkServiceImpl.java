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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.mono.state.codec.MonoMapCodecAdapter;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.network.impl.serdes.EntityNumCodec;
import com.hedera.node.app.service.network.impl.serdes.MonoContextAdapterCodec;
import com.hedera.node.app.service.network.impl.serdes.MonoRunningHashesAdapterCodec;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link NetworkService} {@link com.hedera.node.app.spi.Service}.
 */
public final class NetworkServiceImpl implements NetworkService {
    public static final String CONTEXT_KEY = "CONTEXT";
    public static final String STAKING_KEY = "STAKING";
    public static final String RUNNING_HASHES_KEY = "RUNNING_HASHES";
    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().minor(34).build();

    @Override
    public void registerMonoAdapterSchemas(final @NonNull SchemaRegistry registry) {
        registry.register(networkSchema());
    }

    private Schema networkSchema() {
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(
                        stakingDef(),
                        StateDefinition.singleton(CONTEXT_KEY, new MonoContextAdapterCodec()),
                        StateDefinition.singleton(RUNNING_HASHES_KEY, new MonoRunningHashesAdapterCodec()));
            }
        };
    }

    private StateDefinition<EntityNum, MerkleStakingInfo> stakingDef() {
        final var keySerdes = new EntityNumCodec();
        final var valueSerdes =
                MonoMapCodecAdapter.codecForSelfSerializable(MerkleStakingInfo.CURRENT_VERSION, MerkleStakingInfo::new);
        return StateDefinition.inMemory(STAKING_KEY, keySerdes, valueSerdes);
    }
}
