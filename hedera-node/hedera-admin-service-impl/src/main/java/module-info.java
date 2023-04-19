import com.hedera.node.app.service.admin.impl.FreezeServiceImpl;

module com.hedera.node.app.service.admin.impl {
    requires transitive com.hedera.node.app.service.admin;
    requires static com.github.spotbugs.annotations;
    requires dagger;
    requires javax.inject;
    requires com.hedera.pbj.runtime;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.common;

    provides com.hedera.node.app.service.admin.FreezeService with
            FreezeServiceImpl;

    exports com.hedera.node.app.service.admin.impl to
            com.hedera.node.app,
            com.hedera.node.app.service.admin.impl.test;
    exports com.hedera.node.app.service.admin.impl.handlers;
    exports com.hedera.node.app.service.admin.impl.components;
    exports com.hedera.node.app.service.admin.impl.codec;
}
