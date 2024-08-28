module com.hedera.node.app.service.addressbook {
    exports com.hedera.node.app.service.addressbook;

    uses com.hedera.node.app.service.addressbook.AddressBookService;

    requires transitive com.hedera.node.app.spi;
    requires com.hedera.node.config;
    requires org.testcontainers;
    requires static com.github.spotbugs.annotations;
}
