module com.swirlds.state.impl {
    exports com.swirlds.state.merkle.info;
    exports com.swirlds.state.merkle.singleton;
    exports com.swirlds.state.merkle.queue;
    exports com.swirlds.state.merkle.memory;
    exports com.swirlds.state.merkle.disk;
    exports com.swirlds.state.merkle;

    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.merkle;
    requires transitive com.swirlds.state.api;
    requires transitive com.swirlds.virtualmap;
    requires transitive com.hedera.pbj.runtime;
    requires com.swirlds.fcqueue;
    requires org.apache.logging.log4j;
    requires static transitive com.github.spotbugs.annotations;
}
