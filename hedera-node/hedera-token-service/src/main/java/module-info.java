module com.hedera.node.app.service.token {
    exports com.hedera.node.app.service.token;
    exports com.hedera.node.app.service.token.api to
            com.hedera.node.app.service.contract.impl,
            com.hedera.node.app,
            com.hedera.node.app.service.token.impl,
            com.hedera.node.app.service.token.test.fixtures;
    exports com.hedera.node.app.service.token.records to
            com.hedera.node.app.service.contract.impl,
            com.hedera.node.app,
            com.hedera.node.app.service.token.impl;

    uses com.hedera.node.app.service.token.TokenService;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config.api;
    requires transitive org.apache.logging.log4j;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.node.app.service.evm;
    requires com.swirlds.common;
    requires static transitive com.github.spotbugs.annotations;
}
