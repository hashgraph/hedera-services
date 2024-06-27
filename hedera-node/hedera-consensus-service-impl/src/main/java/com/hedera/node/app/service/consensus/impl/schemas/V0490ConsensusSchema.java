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

package com.hedera.node.app.service.consensus.impl.schemas;

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPICS_KEY;
import static com.hedera.node.app.service.consensus.impl.codecs.ConsensusServiceStateTranslator.stateToPbj;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.common.merkle.proto.MerkleNodeProtoFields;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * General schema for the consensus service
 * (FUTURE) When mod-service release is finalized, rename this class to e.g.
 * {@code Release47ConsensusSchema} as it will no longer be appropriate to assume
 * this schema is always correct for the current version of the software.
 */
public class V0490ConsensusSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V0490ConsensusSchema.class);

    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    private static final long MAX_TOPICS = 1_000_000_000L;

    private static MerkleMap<EntityNum, MerkleTopic> fs;

    public V0490ConsensusSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.onDisk(
                // https://github.com/hashgraph/hedera-services/issues/13781
                // TOPICS_KEY, TopicID.PROTOBUF, Topic.PROTOBUF, MerkleNodeProtoFields.FIELD_KEYVALUEVALUELEAF_TOPICS, MAX_TOPICS));
                TOPICS_KEY, TopicID.PROTOBUF, Topic.PROTOBUF, MerkleNodeProtoFields.FIELD_STATENODE_KVTOPICS, MAX_TOPICS));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        if (fs != null) {
            final var numTopicInsertions = new AtomicLong();
            final var topicStoreRef = new AtomicReference<>(ctx.newStates().<TopicID, Topic>get(TOPICS_KEY));
            log.info("BBM: running consensus migration...");
            MerkleMapLike.from(fs).forEachNode((k, v) -> {
                final var pbjTopic = stateToPbj(v);
                topicStoreRef.get().put(pbjTopic.topicId(), pbjTopic);
                if (numTopicInsertions.incrementAndGet() % 10_000 == 0) {
                    // Make sure we are flushing data to disk as we go
                    ((WritableKVStateBase) topicStoreRef.get()).commit();
                    ctx.copyAndReleaseOnDiskState(TOPICS_KEY);
                    // And ensure we have the latest writable state
                    topicStoreRef.set(ctx.newStates().get(TOPICS_KEY));
                }
            });

            if (topicStoreRef.get().isModified()) ((WritableKVStateBase) topicStoreRef.get()).commit();

            log.info("BBM: finished consensus service migration");
        } else {
            log.warn("BBM: no consensus 'from' state found");
        }

        fs = null;
    }

    public static void setFromState(@Nullable final MerkleMap<EntityNum, MerkleTopic> fs) {
        V0490ConsensusSchema.fs = fs;
    }
}
