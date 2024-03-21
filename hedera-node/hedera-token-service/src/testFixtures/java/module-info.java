module com.hedera.node.app.service.token.test.fixtures {
    exports com.hedera.node.app.service.token.fixtures;

    requires transitive com.hedera.node.app.service.token;
    requires static transitive com.github.spotbugs.annotations;
}
