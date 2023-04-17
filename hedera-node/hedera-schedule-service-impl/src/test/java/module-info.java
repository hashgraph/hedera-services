module com.hedera.node.app.service.schedule.impl.test {
    requires org.junit.jupiter.api;
    requires com.hedera.pbj.runtime;
    requires com.hedera.node.app.service.mono;
    requires transitive com.hedera.node.app.service.schedule.impl;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires com.hedera.node.app.service.mono.testFixtures;
    requires org.apache.commons.lang3;
    requires org.apache.commons.codec;
    requires com.hedera.node.app.spi.fixtures;
    requires com.swirlds.common;
    requires org.assertj.core;

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
