module com.hedera.node.app.service.network.impl {
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.service.network;
    requires com.swirlds.common;
    requires dagger;
    requires javax.inject;
    requires com.github.spotbugs.annotations;

    provides com.hedera.node.app.service.network.NetworkService with
            com.hedera.node.app.service.network.impl.NetworkServiceImpl;

    exports com.hedera.node.app.service.network.impl to
            com.hedera.node.app,
            com.hedera.node.app.service.network.impl.test;
    exports com.hedera.node.app.service.network.impl.handlers;
    exports com.hedera.node.app.service.network.impl.serdes to
            com.hedera.node.app.service.network.impl.test;
    exports com.hedera.node.app.service.network.impl.components;
}
