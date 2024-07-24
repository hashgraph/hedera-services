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

package com.hedera.node.app.service.token.impl.schemas;

import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.state.spi.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class V0530TokenSchema extends StakingInfoManagementSchema {

    private static final long MAX_PENDING_AIRDROPS = 1_000_000_000L;
    public static final String AIRDROPS_KEY = "PENDING_AIRDROPS";

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(53).patch(0).build();

    public V0530TokenSchema() {
        super(VERSION);
    }

    @SuppressWarnings("rawtypes")
    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.onDisk(
                AIRDROPS_KEY, PendingAirdropId.PROTOBUF, PendingAirdropValue.PROTOBUF, MAX_PENDING_AIRDROPS));
    }
}
