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
