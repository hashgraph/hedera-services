module com.hedera.node.app.service.admin.impl {
    requires transitive com.hedera.node.app.service.admin;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.google.auto.service;

    exports com.hedera.node.app.service.admin.impl.handlers;
    exports com.hedera.node.app.service.admin.impl;

    provides com.hedera.node.app.spi.service.ServiceFactory with
            com.hedera.node.app.service.admin.impl.FreezeServiceFactory;
}
