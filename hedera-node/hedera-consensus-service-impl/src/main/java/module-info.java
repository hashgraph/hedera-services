import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;

module com.hedera.node.app.service.consensus.impl {
	requires transitive com.hedera.node.app.service.consensus;
	requires com.hedera.hashgraph.protobuf.java.api;

	provides com.hedera.node.app.service.consensus.ConsensusService with
			ConsensusServiceImpl;

	exports com.hedera.node.app.service.consensus.impl to
			com.hedera.node.app.service.consensus.impl.itest;
	exports com.hedera.node.app.service.consensus.impl.handlers;
}
