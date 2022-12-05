module com.hedera.node.app.service.file.impl.test {
    requires com.hedera.node.app.service.file;
    requires com.hedera.node.app.service.file.impl;
    requires org.junit.jupiter.api;

    opens com.hedera.node.app.service.file.impl.test to
            org.junit.platform.commons;
}
