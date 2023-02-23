import com.hedera.node.app.service.network.impl.NetworkServiceFactory;
import com.hedera.node.app.spi.service.ServiceFactory;

module com.hedera.node.app.service.network.impl {
    requires com.hedera.node.app.service.network;
    requires dagger;
    requires javax.inject;
    requires static com.google.auto.service;

    exports com.hedera.node.app.service.network.impl to
            com.hedera.node.app.service.network.impl.test;
    exports com.hedera.node.app.service.network.impl.handlers;
    exports com.hedera.node.app.service.network.impl.components;

    provides ServiceFactory with NetworkServiceFactory;
}
