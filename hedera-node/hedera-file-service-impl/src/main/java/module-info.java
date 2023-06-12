import com.hedera.node.app.service.file.impl.FileServiceImpl;

module com.hedera.node.app.service.file.impl {
    requires transitive com.hedera.node.app.service.file;
    requires transitive com.hedera.node.app.service.mono;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.config;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.fasterxml.jackson.databind;
    requires com.github.spotbugs.annotations;
    requires com.swirlds.config;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;

    provides com.hedera.node.app.service.file.FileService with
            FileServiceImpl;

    exports com.hedera.node.app.service.file.impl.handlers;
    exports com.hedera.node.app.service.file.impl.codec;
    exports com.hedera.node.app.service.file.impl.records;
    exports com.hedera.node.app.service.file.impl;
    exports com.hedera.node.app.service.file.impl.base;
    exports com.hedera.node.app.service.file.impl.utils;
}
