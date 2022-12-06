import com.hedera.node.app.service.admin.impl.StandardFreezeService;

module com.hedera.node.app.service.admin.impl {
    requires transitive com.hedera.node.app.service.admin;

    provides com.hedera.node.app.service.admin.FreezeService with
            StandardFreezeService;

    exports com.hedera.node.app.service.admin.impl to
            com.hedera.node.app.service.admin.impl.test;
}
