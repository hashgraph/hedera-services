import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;

module com.hedera.node.app.service.consensus.impl {
    requires transitive com.hedera.node.app.service.consensus;
    requires transitive com.hedera.node.app.service.mono;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.merkle;
    requires transitive com.swirlds.state.api;
    requires transitive com.hedera.pbj.runtime;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.node.config;
    requires com.swirlds.platform.core;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
    requires static java.compiler; // javax.annotation.processing.Generated

    provides com.hedera.node.app.service.consensus.ConsensusService with
            ConsensusServiceImpl;

    exports com.hedera.node.app.service.consensus.impl to
            com.hedera.node.app;
    exports com.hedera.node.app.service.consensus.impl.handlers;
    exports com.hedera.node.app.service.consensus.impl.records;
    exports com.hedera.node.app.service.consensus.impl.schemas;
}
