/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test;

import java.util.List;

/**
 * Generates a list of node stakes given a seed and number of nodes. The list of stake values must have a size equal to
 * the specified number of nodes.
 */
@FunctionalInterface
public interface StakeGenerator {

    /**
     * Generate a list of stake values.
     *
     * @param seed
     * 		the seed to use for randomization. May or may not be used depending on the implementation.
     * @param numberOfNodes
     * 		the number of nodes to generate stake for
     * @return a list of stakes equal in size to {@code numberOfNodes}
     */
    List<Long> getStakes(Long seed, int numberOfNodes);
}
