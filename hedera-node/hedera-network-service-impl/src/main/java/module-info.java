import com.hedera.node.app.service.network.impl.NetworkServiceImpl;

module com.hedera.node.app.service.network.impl {
    requires transitive com.hedera.node.app.service.network;

    provides com.hedera.node.app.service.network.NetworkService with
            NetworkServiceImpl;

    exports com.hedera.node.app.service.network.impl to
            com.hedera.node.app.service.network.impl.itest;
    exports com.hedera.node.app.service.network.impl.handlers;
}
