module com.hedera.node.app.service.consensus.impl {
    requires transitive com.hedera.node.app.service.consensus;
    requires com.hedera.hashgraph.protobuf.java.api;

    provides com.hedera.node.app.service.consensus.ConsensusService with
            com.hedera.node.app.service.consensus.impl.StandardConsensusService;

    exports com.hedera.node.app.service.consensus.impl to
            com.hedera.node.app.service.consensus.impl.test;
    exports com.hedera.node.app.service.consensus.impl.handlers;
}
