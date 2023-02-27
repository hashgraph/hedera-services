import com.hedera.node.app.service.consensus.impl.ConsensusServiceFactory;
import com.hedera.node.app.spi.service.ServiceFactory;

module com.hedera.node.app.service.consensus.impl {
    exports com.hedera.node.app.service.consensus.impl;
    exports com.hedera.node.app.service.consensus.impl.handlers;
    
    requires transitive com.hedera.node.app.service.consensus;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.hedera.node.app.service.mono;
    requires static com.google.auto.service;
    requires com.swirlds.common;
    requires com.google.protobuf;

    provides ServiceFactory with
            ConsensusServiceFactory;
}
