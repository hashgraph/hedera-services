module com.swirlds.state.impl {
    exports com.swirlds.state.merkle.singleton;
    exports com.swirlds.state.merkle.queue;
    exports com.swirlds.state.merkle.memory;
    exports com.swirlds.state.merkle.disk;
    exports com.swirlds.state.merkle;

    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.merkle;
    requires transitive com.swirlds.state.api;
    requires com.swirlds.fcqueue;
    requires com.swirlds.merkledb;
}
