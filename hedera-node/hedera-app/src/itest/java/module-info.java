open module com.hedera.node.app.itest {
	requires com.hedera.node.app;
	requires org.junit.jupiter.api;
	requires com.hedera.node.app.spi;
	requires io.grpc;
	requires io.helidon.grpc.client;
	requires io.helidon.grpc.server;
	requires com.swirlds.common;
	requires com.hedera.node.app.service.admin;
	requires com.hedera.node.app.service.consensus;
	requires com.hedera.node.app.service.file;
	requires com.hedera.node.app.service.network;
	requires com.hedera.node.app.service.scheduled;
	requires com.hedera.node.app.service.contract;
	requires com.hedera.node.app.service.token;
	requires com.hedera.node.app.service.util;
}