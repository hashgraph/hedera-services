import com.hedera.node.app.service.token.impl.CryptoServiceFactory;
import com.hedera.node.app.spi.service.ServiceFactory;

module com.hedera.node.app.service.token.impl {
    requires com.hedera.node.app.service.token;
    requires org.apache.commons.lang3;
    requires com.google.common;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.google.protobuf;
    requires com.hedera.node.app.service.evm;
    requires com.hedera.node.app.spi;
    requires static com.google.auto.service;
    requires javax.inject;
    requires dagger;

    exports com.hedera.node.app.service.token.impl to
            com.hedera.node.app;
    exports com.hedera.node.app.service.token.impl.entity;
    exports com.hedera.node.app.service.token.impl.util;
    exports com.hedera.node.app.service.token.impl.handlers to
            com.hedera.node.app;
    exports com.hedera.node.app.service.token.impl.components;

    opens com.hedera.node.app.service.token.impl.util to
            com.hedera.node.app.service.token.impl.test;

    provides ServiceFactory with
            CryptoServiceFactory,
            com.hedera.node.app.service.token.impl.TokenServiceFactory;
}
