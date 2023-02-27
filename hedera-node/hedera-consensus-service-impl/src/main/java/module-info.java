import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;

module com.hedera.node.app.service.consensus.impl {
    requires transitive com.hedera.node.app.service.consensus;
    requires com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.common;
    requires dagger;
    requires javax.inject;
    requires com.github.spotbugs.annotations;
    requires com.hedera.node.app.service.token;
    requires com.swirlds.config;

    provides com.hedera.node.app.service.consensus.ConsensusService with
            ConsensusServiceImpl;

    exports com.hedera.node.app.service.consensus.impl to
            com.hedera.node.app.service.consensus.impl.test,
            com.hedera.node.app;
    exports com.hedera.node.app.service.consensus.impl.handlers;
    exports com.hedera.node.app.service.consensus.impl.components;
    exports com.hedera.node.app.service.consensus.impl.serdes;
    exports com.hedera.node.app.service.consensus.impl.entity;
    exports com.hedera.node.app.service.consensus.impl.config;
    exports com.hedera.node.app.service.consensus.impl.records;
}
