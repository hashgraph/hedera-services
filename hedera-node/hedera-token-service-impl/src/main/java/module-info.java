module com.hedera.node.app.service.token.impl {
    requires com.hedera.node.app.service.token;
    requires static com.github.spotbugs.annotations;
    requires org.apache.commons.lang3;

    provides com.hedera.node.app.service.token.TokenService with
            com.hedera.node.app.service.token.impl.StandardTokenService;
    provides com.hedera.node.app.service.token.CryptoService with
            com.hedera.node.app.service.token.impl.StandardCryptoService;

    exports com.hedera.node.app.service.token.impl to
            com.hedera.node.app.service.token.impl.test;
    exports com.hedera.node.app.service.token.impl.entity to
            com.hedera.node.app.service.token.impl.test;
}
