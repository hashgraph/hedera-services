module com.hedera.node.app.service.token {
    exports com.hedera.node.app.service.token;
    exports com.hedera.node.app.service.token.entity;

    uses com.hedera.node.app.service.token.TokenService;

    requires transitive com.hedera.node.app.spi;
}
