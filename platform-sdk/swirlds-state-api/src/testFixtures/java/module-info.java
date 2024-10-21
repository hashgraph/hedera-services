open module com.swirlds.state.api.test.fixtures {
    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.merkle;
    requires transitive com.swirlds.state.api;
    requires transitive com.swirlds.virtualmap;
    requires transitive com.hedera.pbj.runtime;
    requires transitive org.junit.jupiter.params;
    requires com.swirlds.merkledb;
    requires org.junit.jupiter.api;
    requires static transitive com.github.spotbugs.annotations;

    exports com.swirlds.state.test.fixtures;
    exports com.swirlds.state.test.fixtures.merkle;
}
