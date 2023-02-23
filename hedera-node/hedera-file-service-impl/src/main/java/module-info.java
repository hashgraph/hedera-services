import com.hedera.node.app.service.file.impl.FileServiceFactory;
import com.hedera.node.app.spi.service.ServiceFactory;

module com.hedera.node.app.service.file.impl {
    requires com.hedera.node.app.service.file;
    requires dagger;
    requires javax.inject;
    requires static com.google.auto.service;

    exports com.hedera.node.app.service.file.impl;
    exports com.hedera.node.app.service.file.impl.handlers;
    exports com.hedera.node.app.service.file.impl.components;

    provides ServiceFactory with FileServiceFactory;
}
