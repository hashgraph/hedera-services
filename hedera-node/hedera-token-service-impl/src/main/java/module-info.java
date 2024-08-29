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
    requires transitive com.swirlds.state.api;
    requires transitive com.hedera.pbj.runtime;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.swirlds.base;
    requires com.google.common;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires org.bouncycastle.provider;
    requires static com.github.spotbugs.annotations;
    requires static java.compiler; // javax.annotation.processing.Generated

    provides com.hedera.node.app.service.token.TokenService with
            com.hedera.node.app.service.token.impl.TokenServiceImpl;

    exports com.hedera.node.app.service.token.impl to
            com.hedera.node.app,
            com.hedera.node.app.service.contract.impl,
            com.hedera.node.test.clients;
    exports com.hedera.node.app.service.token.impl.schemas to
            com.hedera.node.app,
            com.hedera.node.test.clients;
    exports com.hedera.node.app.service.token.impl.handlers.staking to
            com.hedera.node.app,
            com.hedera.node.test.clients;
    exports com.hedera.node.app.service.token.impl.handlers to
            com.hedera.node.app,
            com.hedera.node.test.clients;
    exports com.hedera.node.app.service.token.impl.handlers.transfer to
            com.hedera.node.app,
            com.hedera.node.test.clients;
    exports com.hedera.node.app.service.token.impl.api to
            com.hedera.node.app,
            com.hedera.node.app.service.contract.impl,
            com.hedera.node.test.clients;
    exports com.hedera.node.app.service.token.impl.util to
            com.hedera.node.app,
            com.hedera.node.test.clients;
    exports com.hedera.node.app.service.token.impl.validators to
            com.hedera.node.app,
            com.hedera.node.test.clients;
    exports com.hedera.node.app.service.token.impl.comparator;
}
