module com.hedera.node.app.service.network.impl.test {
    requires com.hedera.node.app.service.network;
    requires com.hedera.node.app.service.network.impl;
    requires org.junit.jupiter.api;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires org.assertj.core;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.common;
    requires com.swirlds.fcqueue;

    opens com.hedera.node.app.service.network.impl.test to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.network.impl.test.serdes to
            org.junit.platform.commons;
}
