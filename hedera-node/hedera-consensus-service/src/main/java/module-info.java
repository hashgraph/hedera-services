module com.hedera.node.app.service.consensus {
    exports com.hedera.node.app.service.consensus;

    uses com.hedera.node.app.service.consensus.ConsensusService;

    requires transitive com.hedera.node.app.spi;
    requires static com.github.spotbugs.annotations;
}
