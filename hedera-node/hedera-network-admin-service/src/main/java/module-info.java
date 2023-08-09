module com.hedera.node.app.service.network.admin {
    exports com.hedera.node.app.service.networkadmin;

    uses com.hedera.node.app.service.networkadmin.FreezeService;
    uses com.hedera.node.app.service.networkadmin.NetworkService;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.common;
    requires com.hedera.node.hapi;
    requires static com.github.spotbugs.annotations;
}
