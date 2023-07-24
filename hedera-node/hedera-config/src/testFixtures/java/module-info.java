module com.hedera.node.config.test.fixtures {
    exports com.hedera.node.config.testfixtures;

    requires transitive com.swirlds.config;
    requires com.hedera.node.config;
    requires com.swirlds.common;
    requires transitive com.swirlds.test.framework;
    requires static com.github.spotbugs.annotations;
}
