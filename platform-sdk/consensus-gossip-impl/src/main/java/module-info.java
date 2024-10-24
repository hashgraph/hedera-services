module com.hedera.consensus.gossip.impl {
    requires transitive com.hedera.service.gossip;

    provides com.hedera.service.gossip.GossipService with
            com.hedera.service.gossip.impl.GossipServiceImpl;
}
