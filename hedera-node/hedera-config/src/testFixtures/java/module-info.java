module com.hedera.node.config.test.fixtures {
    exports com.hedera.node.config.testfixtures;

    requires transitive com.swirlds.config;
    requires transitive com.swirlds.test.framework;
    requires com.hedera.node.config;
    requires com.swirlds.common;
    requires com.swirlds.fchashmap;
    requires com.swirlds.jasperdb;
    requires com.swirlds.virtualmap;
    requires static com.github.spotbugs.annotations;
}
