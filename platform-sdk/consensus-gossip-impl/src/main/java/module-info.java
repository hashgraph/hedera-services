module com.hedera.consensus.gossip.impl {
    exports com.hedera.service.gossip.impl;
    exports com.hedera.service.gossip.impl.shadowgraph;

    requires transitive com.swirlds.common;
    requires transitive com.hedera.service.gossip;
    requires com.github.spotbugs.annotations;

    provides com.hedera.service.gossip.GossipService with
            com.hedera.service.gossip.impl.GossipServiceImpl;
}
