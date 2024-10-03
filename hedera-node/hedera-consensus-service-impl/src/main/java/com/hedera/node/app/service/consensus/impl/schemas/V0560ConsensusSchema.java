/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPIC_ALLOWANCES_KEY;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TopicAllowanceId;
import com.hedera.hapi.node.base.TopicAllowanceValue;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class V0560ConsensusSchema extends Schema {
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(56).patch(0).build();

    private static final long MAX_TOPIC_ALLOWANCES = 1_000_000_000L;

    public V0560ConsensusSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.onDisk(
                TOPIC_ALLOWANCES_KEY, TopicAllowanceId.PROTOBUF, TopicAllowanceValue.PROTOBUF, MAX_TOPIC_ALLOWANCES));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        // There are no topic allowances before this version
    }
}
