/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.mono.state.codec.MonoMapCodecAdapter;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.submerkle.ExchangeRates;
import com.hedera.node.app.service.mono.state.submerkle.SequenceNumber;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.networkadmin.NetworkService;
import com.hedera.node.app.service.networkadmin.impl.serdes.EntityNumCodec;
import com.hedera.node.app.service.networkadmin.impl.serdes.MonoContextAdapterCodec;
import com.hedera.node.app.service.networkadmin.impl.serdes.MonoRunningHashesAdapterCodec;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
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
    private static final ImmutableHash GENESIS_HASH = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

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
                        StateDefinition.singleton(CONTEXT_KEY, new MonoContextAdapterCodec()),
                        StateDefinition.singleton(RUNNING_HASHES_KEY, new MonoRunningHashesAdapterCodec()));
            }

            @Override
            public void migrate(@NonNull MigrationContext ctx) {
                final var runningHashState = ctx.newStates().getSingleton(RUNNING_HASHES_KEY);
                RecordsRunningHashLeaf leaf = new RecordsRunningHashLeaf(new RunningHash(GENESIS_HASH));
                runningHashState.put(leaf);

                final var contextState = ctx.newStates().getSingleton(CONTEXT_KEY);
                final var hederaConfig = ctx.configuration().getConfigData(HederaConfig.class);
                final var seqStart = hederaConfig.firstUserEntity();
                contextState.put(new MerkleNetworkContext(
                        null, new SequenceNumber(seqStart), seqStart - 1, new ExchangeRates()));
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
