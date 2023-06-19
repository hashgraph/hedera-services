module com.hedera.node.app.service.util {
    exports com.hedera.node.app.service.util;

    uses com.hedera.node.app.service.util.UtilService;

    requires transitive com.hedera.node.app.spi;
    requires static com.github.spotbugs.annotations;
}
