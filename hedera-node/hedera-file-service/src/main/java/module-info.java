module com.hedera.node.app.service.file {
    exports com.hedera.node.app.service.file;

    uses com.hedera.node.app.service.file.FileService;

    requires transitive com.hedera.node.app.spi;
    requires transitive org.slf4j;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.github.spotbugs.annotations;
}
