import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;

module com.hedera.node.app.service.addressbook.impl {
    requires transitive com.hedera.node.app.service.addressbook;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.config;
    requires transitive com.hedera.node.hapi;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.state.api;
    requires transitive com.hedera.pbj.runtime;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.hedera.node.app.service.token;
    requires com.swirlds.platform.core;
    requires org.apache.logging.log4j;
    requires static transitive java.compiler;
    requires static com.github.spotbugs.annotations;

    provides com.hedera.node.app.service.addressbook.AddressBookService with
            AddressBookServiceImpl;

    exports com.hedera.node.app.service.addressbook.impl to
            com.hedera.node.app,
            com.hedera.node.test.clients;
    exports com.hedera.node.app.service.addressbook.impl.handlers;
    exports com.hedera.node.app.service.addressbook.impl.records;
    exports com.hedera.node.app.service.addressbook.impl.validators;
    exports com.hedera.node.app.service.addressbook.impl.schemas;
}
