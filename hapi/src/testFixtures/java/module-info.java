module com.hedera.node.hapi.test.fixtures {
    exports com.hedera.node.hapi.fixtures;

    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
}
