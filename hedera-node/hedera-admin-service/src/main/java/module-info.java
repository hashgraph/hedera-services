module com.hedera.node.app.service.admin {
    exports com.hedera.node.app.service.admin;

    uses com.hedera.node.app.service.admin.FreezeService;

    requires transitive com.hedera.node.app.spi;
    requires com.hedera.node.hapi;
    requires com.github.spotbugs.annotations;
}
