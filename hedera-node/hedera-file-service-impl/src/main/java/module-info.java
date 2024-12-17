module com.hedera.node.app.service.file.impl {
    requires transitive com.hedera.node.app.hapi.fees;
    requires transitive com.hedera.node.app.hapi.utils;
    requires transitive com.hedera.node.app.service.addressbook;
    requires transitive com.hedera.node.app.service.file;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.config;
    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.state.api;
    requires transitive com.hedera.pbj.runtime;
    requires transitive dagger;
    requires transitive static java.compiler; // javax.annotation.processing.Generated
    requires transitive javax.inject;
    requires com.hedera.node.app.service.addressbook.impl;
    requires com.swirlds.common;
    requires com.fasterxml.jackson.databind;
    requires com.google.protobuf;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;

    exports com.hedera.node.app.service.file.impl.handlers;
    exports com.hedera.node.app.service.file.impl.records;
    exports com.hedera.node.app.service.file.impl;
    exports com.hedera.node.app.service.file.impl.base;
    exports com.hedera.node.app.service.file.impl.utils;
    exports com.hedera.node.app.service.file.impl.schemas;
}
