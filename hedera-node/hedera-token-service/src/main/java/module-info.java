/**
 * Provides the classes necessary to manage Hedera Token Service.
 */
module com.hedera.node.app.service.token {
    exports com.hedera.node.app.service.token;
    exports com.hedera.node.app.service.token.api;
    exports com.hedera.node.app.service.token.records to
            com.hedera.node.app.service.contract.impl,
            com.hedera.node.app,
            com.hedera.node.app.service.token.impl;

    uses com.hedera.node.app.service.token.TokenService;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.state.impl;
    requires transitive com.hedera.pbj.runtime;
    requires transitive org.apache.logging.log4j;
    requires com.hedera.node.app.hapi.utils;
    requires com.swirlds.common;
    requires com.github.spotbugs.annotations;
}
