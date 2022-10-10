module com.hedera.hashgraph.base {
    requires com.hedera.hashgraph.protoparse;
    requires com.hedera.hashgraph.hapi;
    requires com.swirlds.common;
    requires com.swirlds.virtualmap;
    requires com.swirlds.jasperdb;
    requires static com.github.spotbugs.annotations;
    exports com.hedera.hashgraph.base.state;
}
