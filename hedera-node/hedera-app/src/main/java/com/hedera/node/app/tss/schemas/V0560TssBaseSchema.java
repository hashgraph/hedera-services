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

package com.hedera.node.app.tss.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.swirlds.state.merkle.Schema;
import com.swirlds.state.merkle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Schema for the TSS service.
 */
public class V0560TssBaseSchema extends Schema {
    public static final String TSS_MESSAGE_MAP_KEY = "TSS_MESSAGES";
    public static final String TSS_VOTE_MAP_KEY = "TSS_VOTES";
    /**
     * This will at most be equal to the number of nodes in the network.
     */
    private static final long MAX_TSS_MESSAGES = 65_536L;
    /**
     * This will at most be equal to the number of nodes in the network.
     */
    private static final long MAX_TSS_VOTES = 65_536L;

    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(56).patch(0).build();
    /**
     * Create a new instance
     */
    public V0560TssBaseSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.onDisk(
                        TSS_MESSAGE_MAP_KEY,
                        TssMessageMapKey.PROTOBUF,
                        TssMessageTransactionBody.PROTOBUF,
                        MAX_TSS_MESSAGES),
                StateDefinition.onDisk(
                        TSS_VOTE_MAP_KEY, TssVoteMapKey.PROTOBUF, TssVoteTransactionBody.PROTOBUF, MAX_TSS_VOTES));
    }
}
