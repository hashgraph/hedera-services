module com.hedera.hashgraph.app {
    requires com.hedera.services.api;
    requires com.swirlds.common;
    requires com.swirlds.merkle;
    requires com.swirlds.virtualmap;
    requires com.swirlds.jasperdb;
    requires jsr305;

    requires java.desktop; // Shouldn't need this, but Platform requires it for now...
    requires com.hedera.hashgraph.protoparse;
}
