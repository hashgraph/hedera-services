module com.hedera.node.app.service.admin.impl.test {
    requires com.hedera.node.app.service.admin;
    requires com.hedera.node.app.service.admin.impl;
    requires org.junit.jupiter.api;

    opens com.hedera.node.app.service.admin.impl.test to
            org.junit.platform.commons;
}
