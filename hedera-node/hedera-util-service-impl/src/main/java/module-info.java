module com.hedera.node.app.service.util.impl {
    requires com.hedera.node.app.service.util;
    requires static com.github.spotbugs.annotations;

    provides com.hedera.node.app.service.util.UtilService with
            com.hedera.node.app.service.util.impl.StandardUtilService;

    exports com.hedera.node.app.service.util.impl to
            com.hedera.node.app.service.util.impl.test;
}
