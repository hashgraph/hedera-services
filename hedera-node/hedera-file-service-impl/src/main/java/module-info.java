import com.hedera.node.app.service.file.impl.FileServiceImpl;

module com.hedera.node.app.service.file.impl {
    requires com.hedera.node.app.service.file;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.common;
    requires dagger;
    requires javax.inject;
    requires com.github.spotbugs.annotations;
    requires com.hedera.pbj.runtime;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires com.swirlds.config;
    requires com.hedera.node.config;

    provides com.hedera.node.app.service.file.FileService with
            FileServiceImpl;

    exports com.hedera.node.app.service.file.impl.handlers;
    exports com.hedera.node.app.service.file.impl.codec;
    exports com.hedera.node.app.service.file.impl.records;
    exports com.hedera.node.app.service.file.impl;
    exports com.hedera.node.app.service.file.impl.base;
    exports com.hedera.node.app.service.file.impl.utils;
}
