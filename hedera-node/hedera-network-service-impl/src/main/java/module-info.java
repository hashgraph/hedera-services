module com.hedera.node.app.service.network.impl {
    requires com.hedera.node.app.service.network;

    provides com.hedera.node.app.service.network.NetworkService with
            com.hedera.node.app.service.network.impl.StandardNetworkService;

    exports com.hedera.node.app.service.network.impl to
            com.hedera.node.app.service.network.impl.test;
    exports com.hedera.node.app.service.network.impl.handlers;
}
