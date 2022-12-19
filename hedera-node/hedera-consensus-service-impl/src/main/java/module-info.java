import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;

module com.hedera.node.app.service.consensus.impl {
    requires transitive com.hedera.node.app.service.consensus;

    provides ConsensusService with
            ConsensusServiceImpl;

    requires static com.github.spotbugs.annotations;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.service.mono;

    exports com.hedera.node.app.service.consensus.impl to
            com.hedera.node.app.service.consensus.impl.test;
}
