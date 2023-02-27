/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event.source;

import com.swirlds.platform.test.event.generator.GraphGenerator;
import java.util.List;

/**
 * Generates a {@link GraphGenerator} with {@link EventSource} instances that have the provided node stakes.
 */
@FunctionalInterface
public interface StakedGraphGenerator {

    /**
     * Provides an event generator containing event sources with the node stakes provided. Node stakes are applied to
     * event sources in order. For example, event source 0 has a stake equal to {@code nodeStakes.get(0)}.
     *
     * @param nodeStakes
     * 		stakes to apply to the event sources
     * @return a {@link GraphGenerator} with staked event sources
     */
    GraphGenerator<?> getGraphGenerator(List<Long> nodeStakes);
}
