module com.hedera.node.app.service.util {
    exports com.hedera.node.app.service.util;

    uses com.hedera.node.app.service.util.UtilService;

    requires transitive com.hedera.node.app.spi;
    requires transitive org.slf4j;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires static com.github.spotbugs.annotations;
}
