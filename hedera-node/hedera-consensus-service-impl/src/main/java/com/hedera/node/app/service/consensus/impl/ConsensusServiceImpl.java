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
package com.hedera.node.app.service.consensus.impl;

import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.serdes.EntityNumSerdes;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapSerdesAdapter;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link ConsensusService} {@link com.hedera.node.app.spi.Service}.
 */
public final class ConsensusServiceImpl implements ConsensusService {
    private static final String TOPICS_KEY = "TOPICS";

    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().setMinor(34).build();

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        registry.register(consensusSchema());
    }

    private Schema consensusSchema() {
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(topicsDef());
            }
        };
    }

    private StateDefinition<EntityNum, MerkleTopic> topicsDef() {
        final var keySerdes = new EntityNumSerdes();
        final var valueSerdes =
                MonoMapSerdesAdapter.serdesForSelfSerializable(
                        MerkleTopic.CURRENT_VERSION, MerkleTopic::new);
        return StateDefinition.inMemory(TOPICS_KEY, keySerdes, valueSerdes);
    }
}
