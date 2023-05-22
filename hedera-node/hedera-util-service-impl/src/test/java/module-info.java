module com.hedera.node.app.service.util.impl.test {
    requires com.hedera.node.app.service.networkadmin;
    requires com.hedera.node.app.service.util.impl;
    requires com.hedera.node.app.spi.test.fixtures;
    requires com.swirlds.common;
    requires org.assertj.core;
    requires org.junit.jupiter.api;
    requires org.mockito.junit.jupiter;
    requires org.mockito;

    opens com.hedera.node.app.service.util.impl.test to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.util.impl.test.handlers to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.util.impl.test.config to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.util.impl.test.records to
            org.junit.platform.commons,
            org.mockito;
}
