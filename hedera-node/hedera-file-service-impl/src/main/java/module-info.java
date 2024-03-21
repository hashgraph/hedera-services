module com.hedera.node.app.service.file.impl {
    requires com.fasterxml.jackson.databind;
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires com.swirlds.config.api;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires transitive com.hedera.node.app.hapi.fees;
    requires transitive com.hedera.node.app.hapi.utils;
    requires transitive com.hedera.node.app.service.file;
    requires transitive com.hedera.node.app.service.mono;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.config;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive dagger;
    requires transitive javax.inject;
    requires static transitive com.github.spotbugs.annotations;
    requires static java.compiler; // javax.annotation.processing.Generated

    exports com.hedera.node.app.service.file.impl.handlers;
    exports com.hedera.node.app.service.file.impl.codec;
    exports com.hedera.node.app.service.file.impl.records;
    exports com.hedera.node.app.service.file.impl;
    exports com.hedera.node.app.service.file.impl.base;
    exports com.hedera.node.app.service.file.impl.utils;
    exports com.hedera.node.app.service.file.impl.schemas;
}
