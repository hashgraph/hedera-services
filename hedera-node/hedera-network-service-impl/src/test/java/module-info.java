module com.hedera.node.app.service.network.impl.test {
    requires com.hedera.node.app.service.network;
    requires com.hedera.node.app.service.network.impl;
    requires org.junit.jupiter.api;

    opens com.hedera.node.app.service.network.impl.test to
            org.junit.platform.commons;
}
