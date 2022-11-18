module com.hedera.node.app.service.token.impl {
    requires com.hedera.node.app.service.token;
    requires static com.github.spotbugs.annotations;

    provides com.hedera.node.app.service.token.TokenService with
            com.hedera.node.app.service.token.impl.StandardTokenService;

    exports com.hedera.node.app.service.token.impl to
            com.hedera.node.app.service.token.impl.test;
}
