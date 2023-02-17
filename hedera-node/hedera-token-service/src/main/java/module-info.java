module com.hedera.node.app.service.token {
    exports com.hedera.node.app.service.token;
    exports com.hedera.node.app.service.token.entity;
    exports com.hedera.node.app.service.token.handlers;

    uses com.hedera.node.app.service.token.TokenService;
    uses com.hedera.node.app.service.token.CryptoService;

    requires transitive com.hedera.node.app.spi;
}
