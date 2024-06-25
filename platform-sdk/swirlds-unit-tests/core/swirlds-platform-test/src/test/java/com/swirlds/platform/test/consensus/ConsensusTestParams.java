/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.consensus;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.WeightGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;

public record ConsensusTestParams(
        @NonNull PlatformContext platformContext,
        int numNodes,
        @NonNull WeightGenerator weightGenerator,
        @NonNull String weightDesc,
        long... seeds) {
    @Override
    public String toString() {
        return numNodes + " nodes, " + weightDesc;
    }
}
