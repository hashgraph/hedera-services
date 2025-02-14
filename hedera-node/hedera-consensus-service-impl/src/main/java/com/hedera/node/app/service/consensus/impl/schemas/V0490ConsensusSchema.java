// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.consensus.impl.schemas;

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPICS_KEY;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Genesis schema for the consensus service.
 *
 * <p>See <a href="https://github.com/hashgraph/hedera-services/tree/release/0.49">this branch</a> for the
 * details of the migration from mono state.
 */
public class V0490ConsensusSchema extends Schema {
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    private static final long MAX_TOPICS = 1_000_000_000L;

    /**
     * Constructor for this schema.
     */
    public V0490ConsensusSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.onDisk(TOPICS_KEY, TopicID.PROTOBUF, Topic.PROTOBUF, MAX_TOPICS));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        // There are no topics at genesis
    }
}
