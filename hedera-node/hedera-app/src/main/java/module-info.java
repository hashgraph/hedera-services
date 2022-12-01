module com.hedera.node.app {
    requires io.helidon.grpc.core;
    requires io.helidon.grpc.server;
    requires com.swirlds.common;
    requires org.slf4j;
    requires static com.github.spotbugs.annotations;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires io.grpc.stub;
}
