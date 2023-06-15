module com.hedera.node.app.service.consensus {
    exports com.hedera.node.app.service.consensus;

    uses com.hedera.node.app.service.consensus.ConsensusService;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires static com.github.spotbugs.annotations;
}
