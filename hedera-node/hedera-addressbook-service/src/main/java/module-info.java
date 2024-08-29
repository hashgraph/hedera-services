module com.hedera.node.app.service.addressbook {
    exports com.hedera.node.app.service.addressbook;

    uses com.hedera.node.app.service.addressbook.AddressBookService;

    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.node.hapi;
    requires transitive com.hedera.pbj.runtime;
    requires org.testcontainers;
    requires static com.github.spotbugs.annotations;
}
