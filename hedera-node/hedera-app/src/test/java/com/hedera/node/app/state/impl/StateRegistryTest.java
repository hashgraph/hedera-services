/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.impl;

import com.hedera.node.app.state.merkle.HederaStateImpl;
import com.hedera.node.app.state.merkle.ServiceStateNode;
import org.junit.jupiter.api.Test;

class StateRegistryTest {
    @Test
    void foo() {
        // Create the merkle state tree
        final var state = new HederaStateImpl();

        // Create a fake "consensus service" and add it to the merkle tree
        final var consensusService = new ServiceStateNode("Consensus Service");
        state.addServiceStateNode(consensusService);

        // Now, pretend we're going to create a StateRegistry to pass to the ConsensusService
        // in its constructor, and let it create the "topics" merkle map.
        final var registry = new StateRegistryImpl(consensusService);
        //        registry.registerOrMigrate("topics", (definer, opt) -> {
        //            return definer.inMemory().define();
        //        });
    }
}
