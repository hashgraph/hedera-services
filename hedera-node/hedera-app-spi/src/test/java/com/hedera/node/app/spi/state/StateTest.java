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
package com.hedera.node.app.spi.state;

import org.junit.jupiter.api.Test;

/** Tests a more complete and elaborate usage of the various state classes. */
class StateTest {
    @Test
    void foo() {
        //        // Create the merkle state tree
        //        var state = new HederaStateImpl();
        //
        //        // Create a fake "consensus service" and add it to the merkle tree
        //        var consensusService = new ServiceStateNode("Consensus Service");
        //        state.putServiceStateIfAbsent(consensusService);
        //
        //        // Now, pretend we're going to create a StateRegistry to pass to the
        // ConsensusService
        //        // in its constructor, and let it create the "topics" merkle map. This is the
        // genesis
        //        // use case, where there is no existing data or version.
        //        var currentVersion = new BasicSoftwareVersion(1);
        //        var previousVersion = SoftwareVersion.NO_VERSION;
        //        var registry = new StateRegistryImpl(ConstructableRegistry.getInstance(),
        // consensusService, currentVersion, previousVersion);
        //
        //        // Initially there should not be a "TOPICS" state because this is genesis
        //        WritableState<EntityNum, MerkleTopic> topicsState = registry.getState("TOPICS");
        //        assertNull(topicsState);
        //
        //        // Create a new state
        //        topicsState = registry.defineNewState("TOPICS").inMemory();
        //        assertNotNull(topicsState);
        //
        //        // Populate the state with some initial values
        //        final var topic1 = new MerkleTopic("Memo 1", null, null, 86400, null, null);
        //        topicsState.put(new EntityNum(1001), topic1);
        //
        //        // None of these values are yet in the underlying merkle tree. So commit them.
        //        ((InMemoryState<EntityNum, MerkleTopic>) topicsState).commit();
        //
        //        // We can now throw away the topicsState, and get a new one. But first lets
        //        // make a fast copy of the state, and get a new topicsState, and put some
        //        // more stuff in it.
        //        state = state.copy();
        //        var states = state.createWritableStates("Consensus Service");
        //        topicsState = states.get("TOPICS");
        //        var opt = topicsState.get(new EntityNum(1001));
        //        assertTrue(opt.isPresent());
        //        assertEquals(topic1, opt.get());
        //        final var topic2 = new MerkleTopic("Memo 2", null, null, 86400, null, null);
        //        topicsState.put(new EntityNum(1002), topic2);
    }
}
