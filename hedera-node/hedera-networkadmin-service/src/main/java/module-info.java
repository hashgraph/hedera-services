module com.hedera.node.app.service.networkadmin {
    exports com.hedera.node.app.service.networkadmin;

    uses com.hedera.node.app.service.networkadmin.FreezeService;
    uses com.hedera.node.app.service.networkadmin.NetworkService;

    requires transitive com.hedera.node.app.spi;
    requires com.hedera.node.hapi;
    requires com.github.spotbugs.annotations;
    requires com.swirlds.common;
}
