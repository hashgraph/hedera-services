module com.hedera.node.app.service.util {
    exports com.hedera.node.app.service.util;

    uses com.hedera.node.app.service.util.UtilService;

    requires com.hedera.node.hapi;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.pbj.runtime;
    requires static transitive com.github.spotbugs.annotations;
}
