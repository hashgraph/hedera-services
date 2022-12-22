import com.hedera.node.app.service.network.impl.NetworkServiceImpl;

module com.hedera.node.app.service.network.impl {
    requires com.hedera.node.app.service.network;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.github.spotbugs.annotations;

    provides com.hedera.node.app.service.network.NetworkService with
            NetworkServiceImpl;

    exports com.hedera.node.app.service.network.impl to
            com.hedera.node.app.service.network.impl.test;
    exports com.hedera.node.app.service.network.impl.handlers;
}
