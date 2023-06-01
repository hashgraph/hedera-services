module com.hedera.node.config {
    exports com.hedera.node.config;
    exports com.hedera.node.config.converter;
    exports com.hedera.node.config.data;
    exports com.hedera.node.config.sources;
    exports com.hedera.node.config.types;
    exports com.hedera.node.config.validation;

    requires transitive com.hedera.node.app.hapi.utils;
    requires transitive com.hedera.node.app.service.mono;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config;
    requires com.github.spotbugs.annotations;
    requires com.swirlds.common;
}
