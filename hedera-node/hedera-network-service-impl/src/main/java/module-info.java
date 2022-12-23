import com.hedera.node.app.service.network.impl.NetworkServiceImpl;

module com.hedera.node.app.service.network.impl {
	requires com.hedera.node.app.service.network;

	provides com.hedera.node.app.service.network.NetworkService with
			NetworkServiceImpl;
	provides com.hedera.node.app.spi.Service with NetworkServiceImpl;

	exports com.hedera.node.app.service.network.impl to
			com.hedera.node.app.service.network.impl.test;
	exports com.hedera.node.app.service.network.impl.handlers;
}
