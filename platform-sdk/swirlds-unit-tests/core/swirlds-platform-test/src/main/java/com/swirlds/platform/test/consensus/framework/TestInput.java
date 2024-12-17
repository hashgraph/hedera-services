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

package com.swirlds.platform.test.consensus.framework;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.WeightGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Holds the input to a consensus test.
 * @param platformContext the {@link PlatformContext} to use for the test
 * @param numberOfNodes the number of nodes in the test
 * @param weightGenerator the {@link WeightGenerator} to use for the test
 * @param seed the seed for the random number generator
 * @param eventsToGenerate the number of events to generate
 */
public record TestInput(
        @NonNull PlatformContext platformContext,
        int numberOfNodes,
        @NonNull WeightGenerator weightGenerator,
        long seed,
        int eventsToGenerate) {

    /**
     * Create a copy of the test input with updated number of nodes.
     *
     * @param numberOfNodes the new number of nodes
     * @return a new {@link TestInput} with the updated number of nodes
     */
    public @NonNull TestInput setNumberOfNodes(int numberOfNodes) {
        return new TestInput(platformContext, numberOfNodes, weightGenerator, seed, eventsToGenerate);
    }
}
