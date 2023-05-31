module com.hedera.node.config {
    exports com.hedera.node.config;
    exports com.hedera.node.config.converter;
    exports com.hedera.node.config.data;
    exports com.hedera.node.config.sources;
    exports com.hedera.node.config.types;
    exports com.hedera.node.config.validation;

    requires static com.github.spotbugs.annotations;
    requires com.swirlds.config;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.hapi;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.pbj.runtime;
}
