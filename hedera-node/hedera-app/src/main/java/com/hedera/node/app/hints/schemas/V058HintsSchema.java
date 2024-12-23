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

package com.hedera.node.app.hints.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsId;
import com.hedera.hapi.node.state.hints.HintsKey;
import com.hedera.hapi.node.state.hints.NodeSchemeId;
import com.hedera.hapi.node.state.hints.PreprocessVoteId;
import com.hedera.hapi.node.state.hints.PreprocessedKeysVote;
import com.hedera.hapi.node.state.primitives.ProtoInteger;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.node.app.hints.HintsService;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Registers the states needed for the {@link HintsService}; these are,
 * <ul>
 *     <li>A singleton with the number of constructions attempted.</li>
 *     <li>A singleton with the active hinTS construction (must be at least
 *     ongoing once the network is active; and until complete, the hinTS service
 *     will not be able to aggregate partial signatures).</li>
 *     <li>A singleton with the next hinTS construction (may or may not be
 *     ongoing, as there may not be a candidate roster set).</li>
 *     <li>A map from universe size to the highest party id in use.</li>
 *     <li>A map from pair of universe size and node id to party id.</li>
 *     <li>A map from pair of party id and universe size to the party's
 *     timestamped key and hints; and, if applicable, the key and hints
 *     it wants to start using for all constructions of comparable size
 *     that begin after the current one ends.</li>
 *     <li>A map from pair of node id and construction id to the node's
 *     vote for the keys aggregated by preprocessing that construction.</li>
 * </ul>
 */
public class V058HintsSchema extends Schema {
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().minor(58).build();

    private static final long MAX_HINTS = 1L << 31;
    private static final long MAX_SIZE_LOG_2 = 32;
    private static final long MAX_PREPROCESSING_VOTES = 1L << 30;

    public static final String NEXT_CONSTRUCTION_ID_KEY = "NEXT_CONSTRUCTION_ID";
    public static final String ACTIVE_CONSTRUCTION_KEY = "ACTIVE_CONSTRUCTION";
    public static final String NEXT_CONSTRUCTION_KEY = "NEXT_CONSTRUCTION";
    public static final String HIGHEST_PARTY_IDS_KEY = "HIGHEST_PARTY_IDS";
    public static final String PARTY_IDS_KEY = "PARTY_IDS";
    public static final String HINTS_KEY = "HINTS";
    public static final String PREPROCESSING_VOTES_KEY = "PREPROCESSING_VOTES";

    public V058HintsSchema() {
        super(VERSION);
    }

    @Override
    public @NonNull Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.singleton(NEXT_CONSTRUCTION_ID_KEY, ProtoLong.PROTOBUF),
                StateDefinition.singleton(ACTIVE_CONSTRUCTION_KEY, HintsConstruction.PROTOBUF),
                StateDefinition.singleton(NEXT_CONSTRUCTION_KEY, HintsConstruction.PROTOBUF),
                StateDefinition.onDisk(
                        HIGHEST_PARTY_IDS_KEY, ProtoInteger.PROTOBUF, ProtoLong.PROTOBUF, MAX_SIZE_LOG_2),
                StateDefinition.onDisk(PARTY_IDS_KEY, NodeSchemeId.PROTOBUF, ProtoLong.PROTOBUF, MAX_HINTS),
                StateDefinition.onDisk(HINTS_KEY, HintsId.PROTOBUF, HintsKey.PROTOBUF, MAX_HINTS),
                StateDefinition.onDisk(
                        PREPROCESSING_VOTES_KEY,
                        PreprocessVoteId.PROTOBUF,
                        PreprocessedKeysVote.PROTOBUF,
                        MAX_PREPROCESSING_VOTES));
    }
}
