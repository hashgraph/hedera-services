import com.hedera.node.app.service.util.impl.UtilServiceImpl;

module com.hedera.node.app.service.util.impl {
    requires transitive com.hedera.node.app.service.util;
    requires transitive com.hedera.node.app.spi;
    requires transitive com.hedera.pbj.runtime;
    requires transitive dagger;
    requires transitive javax.inject;
    requires com.hedera.node.hapi;
    requires org.apache.logging.log4j;
    requires static transitive com.github.spotbugs.annotations;
    requires static java.compiler; // javax.annotation.processing.Generated

    provides com.hedera.node.app.service.util.UtilService with
            UtilServiceImpl;

    exports com.hedera.node.app.service.util.impl to
            com.hedera.node.app;
    exports com.hedera.node.app.service.util.impl.handlers;
    exports com.hedera.node.app.service.util.impl.components;
    exports com.hedera.node.app.service.util.impl.records;
}
