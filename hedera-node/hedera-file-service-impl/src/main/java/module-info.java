import com.hedera.node.app.service.file.impl.FileServiceFactory;
import com.hedera.node.app.spi.service.ServiceFactory;

module com.hedera.node.app.service.file.impl {
    requires com.hedera.node.app.service.file;
    requires static com.google.auto.service;
    requires com.hedera.node.app.service.mono;

    exports com.hedera.node.app.service.file.impl;
    exports com.hedera.node.app.service.file.impl.handlers;

    provides ServiceFactory with
            FileServiceFactory;
}
