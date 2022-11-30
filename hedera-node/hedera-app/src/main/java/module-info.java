module com.hedera.node.app {
	requires com.swirlds.common;
	requires io.helidon.grpc.server;
	requires static com.github.spotbugs.annotations;
	requires com.hedera.hashgraph.protobuf.java.api;
	requires org.slf4j;
}