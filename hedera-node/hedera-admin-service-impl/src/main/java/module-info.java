import com.hedera.node.app.service.admin.impl.FreezeServiceImpl;

module com.hedera.node.app.service.admin.impl {
    requires transitive com.hedera.node.app.service.admin;

    provides com.hedera.node.app.service.admin.FreezeService with
            FreezeServiceImpl;

    requires static com.github.spotbugs.annotations;

    exports com.hedera.node.app.service.admin.impl to
            com.hedera.node.app.service.admin.impl.test;
}
