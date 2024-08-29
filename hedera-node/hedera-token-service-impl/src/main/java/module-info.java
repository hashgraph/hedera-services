/**
 * Module that provides the implementation of the Hedera Token Service.
 */
module com.hedera.node.app.service.token.impl {
    requires transitive com.hedera.node.app.hapi.fees;
    requires transitive com.hedera.node.app.hapi.utils;
    requires transitive com.hedera.node.app.service.token;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.config;
    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.metrics.api;
    requires transitive com.swirlds.state.api;
    requires transitive com.hedera.pbj.runtime;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires com.google.common;
    requires com.hedera.evm;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires org.bouncycastle.provider;
    requires static com.github.spotbugs.annotations;
    requires static java.compiler; // javax.annotation.processing.Generated

    provides com.hedera.node.app.service.token.TokenService with
            com.hedera.node.app.service.token.impl.TokenServiceImpl;

    exports com.hedera.node.app.service.token.impl.handlers to
            com.hedera.node.app,
            com.hedera.node.app.service.token.impl.test;
    exports com.hedera.node.app.service.token.impl;
    exports com.hedera.node.app.service.token.impl.api to
            com.hedera.node.app,
            com.hedera.node.app.service.token.impl.api.test;
    exports com.hedera.node.app.service.token.impl.validators;
    exports com.hedera.node.app.service.token.impl.util to
            com.hedera.node.app,
            com.hedera.node.app.service.token.impl.test;
    exports com.hedera.node.app.service.token.impl.handlers.staking to
            com.hedera.node.app,
            com.hedera.node.app.service.token.impl.test;
    exports com.hedera.node.app.service.token.impl.handlers.transfer to
            com.hedera.node.app;
    exports com.hedera.node.app.service.token.impl.schemas to
            com.hedera.node.app,
            com.hedera.node.app.service.token.impl.api.test,
            com.hedera.node.test.clients;
}
