module com.hedera.node.app.service.admin.impl {
    exports com.hedera.node.app.service.admin.impl;
    exports com.hedera.node.app.service.admin.impl.handlers;
    requires transitive com.hedera.node.app.service.admin;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.google.auto.service;

    provides com.hedera.node.app.spi.service.ServiceFactory with
            com.hedera.node.app.service.admin.impl.FreezeServiceFactory;
}
