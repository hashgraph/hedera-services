module com.hedera.node.app {
    requires io.helidon.webserver;
    requires io.helidon.webserver.http2;
    requires io.helidon.grpc.server;
    requires io.helidon.grpc.core;
    requires jsr305;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.swirlds.platform;
    requires com.hedera.node.app.spi;
    requires org.slf4j;
}
