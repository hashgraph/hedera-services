module com.hedera.node.app {
    requires io.helidon.grpc.core;
    requires io.helidon.grpc.server;
    requires com.swirlds.common;
    requires org.slf4j;
    requires static com.github.spotbugs.annotations;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires grpc.stub;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.spi;
    requires com.hedera.node.app.service.admin;
    requires com.hedera.node.app.service.consensus;
    requires com.hedera.node.app.service.contract;
    requires com.hedera.node.app.service.file;
    requires com.hedera.node.app.service.network;
    requires com.hedera.node.app.service.scheduled;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.service.util;
}
