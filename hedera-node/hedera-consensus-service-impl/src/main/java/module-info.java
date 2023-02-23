import com.hedera.node.app.service.consensus.impl.ConsensusServiceFactory;
import com.hedera.node.app.spi.service.ServiceFactory;

module com.hedera.node.app.service.consensus.impl {
    requires transitive com.hedera.node.app.service.consensus;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.hedera.node.app.service.mono;
    requires dagger;
    requires javax.inject;
    requires static com.google.auto.service;

    exports com.hedera.node.app.service.consensus.impl.handlers;
    exports com.hedera.node.app.service.consensus.impl.components;
    exports com.hedera.node.app.service.consensus.impl;

    provides ServiceFactory with
            ConsensusServiceFactory;
}
