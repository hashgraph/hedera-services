module com.hedera.node.hapi.test.fixtures {
    exports com.hedera.node.hapi.fixtures;

    requires transitive com.hedera.pbj.runtime;
    requires com.hedera.node.hapi;
    requires com.github.spotbugs.annotations;
}
