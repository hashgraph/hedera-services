module com.hedera.node.app.service.token.impl.test {
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.service.token.impl;
    requires org.junit.jupiter.api;

    opens com.hedera.node.app.service.token.impl.test to
            org.junit.platform.commons;
}
