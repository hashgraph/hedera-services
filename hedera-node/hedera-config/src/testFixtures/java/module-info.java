module com.hedera.node.config.test.fixtures {
    exports com.hedera.node.config.testfixtures;

    requires transitive com.hedera.node.config;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.config.extensions.test.fixtures;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.node.hapi;
    requires com.swirlds.common;
    requires com.swirlds.merkledb;
    requires com.swirlds.platform.core;
    requires com.swirlds.virtualmap;
    requires static com.github.spotbugs.annotations;
}
