module com.hedera.node.app.service.token.impl {
    requires transitive com.hedera.node.app.service.mono;
    requires transitive com.hedera.node.app.service.token;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.hedera.node.app.service.evm;
    requires com.github.spotbugs.annotations;
    requires com.google.common;
    requires com.google.protobuf;
    requires com.swirlds.jasperdb;
    requires org.apache.commons.lang3;

    provides com.hedera.node.app.service.token.TokenService with
            com.hedera.node.app.service.token.impl.TokenServiceImpl;

    exports com.hedera.node.app.service.token.impl.handlers to
            com.hedera.node.app.service.token.impl.test,
            com.hedera.node.app;
    exports com.hedera.node.app.service.token.impl.serdes;
    exports com.hedera.node.app.service.token.impl;
    exports com.hedera.node.app.service.token.impl.records to
            com.hedera.node.app.service.token.impl.test,
            com.hedera.node.app;
    exports com.hedera.node.app.service.token.impl.validators;
    exports com.hedera.node.app.service.token.impl.config to
            com.hedera.node.app.service.token.impl.test,
            com.hedera.node.app;
}
