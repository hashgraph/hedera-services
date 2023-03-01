module com.hedera.node.app.service.consensus {
    exports com.hedera.node.app.service.consensus;
    exports com.hedera.node.app.service.consensus.entity;

    uses com.hedera.node.app.service.consensus.ConsensusService;

    requires transitive com.hedera.node.app.spi;
}
