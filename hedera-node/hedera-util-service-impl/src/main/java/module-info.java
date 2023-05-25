import com.hedera.node.app.service.util.impl.UtilServiceImpl;

module com.hedera.node.app.service.util.impl {
    requires com.hedera.node.app.service.util;
    requires com.github.spotbugs.annotations;
    requires javax.inject;
    requires dagger;
    requires com.hedera.pbj.runtime;
    requires com.hedera.node.hapi;
    requires com.swirlds.common;
    requires org.apache.logging.log4j;
    requires com.google.common;
    requires com.hedera.node.app.service.networkadmin;
    requires com.swirlds.config;
    requires com.hedera.node.config;

    provides com.hedera.node.app.service.util.UtilService with
            UtilServiceImpl;

    exports com.hedera.node.app.service.util.impl to
            com.hedera.node.app;
    exports com.hedera.node.app.service.util.impl.handlers;
    exports com.hedera.node.app.service.util.impl.components;
    exports com.hedera.node.app.service.util.impl.records;
}
