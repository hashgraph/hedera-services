import com.hedera.node.app.spi.service.ServiceFactory;

module com.hedera.node.app.service.util.impl {
    requires com.hedera.node.app.service.util;
    requires static com.google.auto.service;

    exports com.hedera.node.app.service.util.impl;
    exports com.hedera.node.app.service.util.impl.handlers;

    provides ServiceFactory with
            com.hedera.node.app.service.util.impl.UtilServiceFactory;
}
