/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.schemas;

import com.hedera.hapi.node.base.LambdaID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.lambda.LambdaState;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class V061ContractSchema extends Schema {
    private static final int MAX_LAMBDA_STATES = 1_000_000_000;

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(61).build();

    public static final String LAMBDA_STATES_KEY = "LAMBDA_STATES";

    public V061ContractSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    public Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.onDisk(LAMBDA_STATES_KEY, LambdaID.PROTOBUF, LambdaState.PROTOBUF, MAX_LAMBDA_STATES));
    }
}
