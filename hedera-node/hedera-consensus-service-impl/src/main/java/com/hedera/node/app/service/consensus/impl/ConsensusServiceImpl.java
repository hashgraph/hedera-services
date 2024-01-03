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

package com.hedera.node.app.service.consensus.impl;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.codecs.ConsensusServiceStateTranslator;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link ConsensusService} {@link com.hedera.node.app.spi.Service}.
 */
public final class ConsensusServiceImpl implements ConsensusService {
    public static final long RUNNING_HASH_VERSION = 3L;
    public static final int RUNNING_HASH_BYTE_ARRAY_SIZE = 48;
    public static final String TOPICS_KEY = "TOPICS";

    private MerkleMap<EntityNum, MerkleTopic> fs;

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry, final SemanticVersion version) {
        // We intentionally ignore the given (i.e. passed-in) version in this method
        registry.register(consensusSchema(RELEASE_045_VERSION));

        //        if(true)return;
        registry.register(new Schema(RELEASE_MIGRATION_VERSION) {

            @Override
            public void migrate(@NonNull MigrationContext ctx) {
                System.out.println("BBM: running consensus migration...");

                var ts = ctx.newStates().<TopicID, Topic>get(TOPICS_KEY);
                ConsensusServiceStateTranslator.migrateFromMerkleToPbj(fs, ts);
                if (ts.isModified()) ((WritableKVStateBase) ts).commit();

                fs = null;

                System.out.println("BBM: finished consensus");
            }
        });
    }

    public void setFromState(MerkleMap<EntityNum, MerkleTopic> fs) {
        this.fs = fs;
    }

    private Schema consensusSchema(final SemanticVersion version) {
        return new Schema(version) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(StateDefinition.inMemory(TOPICS_KEY, TopicID.PROTOBUF, Topic.PROTOBUF));
            }
        };
    }
}
