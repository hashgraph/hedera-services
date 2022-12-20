import com.hedera.node.app.service.token.impl.StandardCryptoService;

module com.hedera.node.app.service.token.impl {
    requires com.hedera.node.app.service.token;
    requires org.apache.commons.lang3;
    requires com.google.common;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.google.protobuf;
    requires com.hedera.node.app.service.evm;

    provides com.hedera.node.app.service.token.TokenService with
            com.hedera.node.app.service.token.impl.StandardTokenService;
    provides com.hedera.node.app.service.token.CryptoService with
            StandardCryptoService;

    exports com.hedera.node.app.service.token.impl to
            com.hedera.node.app.service.token.impl.test;
    exports com.hedera.node.app.service.token.impl.entity to
            com.hedera.node.app.service.token.impl.test;
    exports com.hedera.node.app.service.token.impl.util to
            com.hedera.node.app.service.token.impl.test;

    opens com.hedera.node.app.service.token.impl.util to
            com.hedera.node.app.service.token.impl.test;
}
