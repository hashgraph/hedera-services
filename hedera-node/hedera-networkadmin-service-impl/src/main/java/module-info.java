import com.hedera.node.app.service.networkadmin.NetworkService;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;

module com.hedera.node.app.service.networkadmin.impl {
    requires transitive com.hedera.node.app.service.networkadmin;
    requires com.github.spotbugs.annotations;
    requires dagger;
    requires javax.inject;
    requires com.hedera.pbj.runtime;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.common;
    requires com.swirlds.config;
    requires org.apache.logging.log4j;

    provides com.hedera.node.app.service.networkadmin.FreezeService with
            FreezeServiceImpl;
    provides NetworkService with
            com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;

    exports com.hedera.node.app.service.networkadmin.impl to
            com.hedera.node.app,
            com.hedera.node.app.service.networkadmin.impl.test;
    exports com.hedera.node.app.service.networkadmin.impl.handlers;
    exports com.hedera.node.app.service.networkadmin.impl.codec;
    exports com.hedera.node.app.service.networkadmin.impl.serdes to
            com.hedera.node.app.service.networkadmin.impl.test;
    exports com.hedera.node.app.service.networkadmin.impl.config;
}
