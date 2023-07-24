module com.hedera.node.app.service.schedule.impl.test {
    requires transitive com.hedera.node.app.service.schedule.impl;
    requires com.hedera.node.app.service.mono.test.fixtures;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.spi.test.fixtures;
    requires com.swirlds.common;
    requires org.junit.jupiter.api;
    requires org.mockito.junit.jupiter;
    requires org.mockito;

    opens com.hedera.node.app.service.schedule.impl.test to
            org.junit.platform.commons,
            org.mockito;

    exports com.hedera.node.app.service.schedule.impl.test to
            org.mockito;

    opens com.hedera.node.app.service.schedule.impl.test.serdes to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.schedule.impl.test.handlers to
            org.junit.platform.commons,
            org.mockito;
}
