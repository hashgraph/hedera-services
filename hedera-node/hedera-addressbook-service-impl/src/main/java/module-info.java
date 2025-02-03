module com.hedera.node.app.service.addressbook.impl {
    requires transitive com.hedera.node.app.service.addressbook;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.config;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.state.api;
    requires transitive dagger;
    requires transitive javax.inject;
    requires transitive org.apache.logging.log4j;
    requires com.hedera.node.app.service.token;
    requires com.swirlds.common;
    requires com.swirlds.platform.core;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires static transitive java.compiler;
    requires static com.github.spotbugs.annotations;

    exports com.hedera.node.app.service.addressbook.impl;
    exports com.hedera.node.app.service.addressbook.impl.handlers;
    exports com.hedera.node.app.service.addressbook.impl.records;
    exports com.hedera.node.app.service.addressbook.impl.validators;
    exports com.hedera.node.app.service.addressbook.impl.schemas;
    exports com.hedera.node.app.service.addressbook.impl.helpers;
}
