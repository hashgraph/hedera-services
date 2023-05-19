module com.hedera.node.app.service.networkadmin.impl.test {
    requires com.hedera.node.app.service.networkadmin;
    requires com.hedera.node.app.service.networkadmin.impl;
    requires com.hedera.pbj.runtime;
    requires org.junit.jupiter.api;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.common;
    requires com.swirlds.fcqueue;
    requires com.google.protobuf;
    requires com.hedera.node.app.spi.fixtures;
    requires com.hedera.node.app.service.token;
    requires org.assertj.core;
    requires static com.github.spotbugs.annotations;

    opens com.hedera.node.app.service.networkadmin.impl.test to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.networkadmin.impl.test.handlers to
            com.hedera.node.app.spi.fixtures,
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.networkadmin.impl.test.codec to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.networkadmin.impl.test.serdes to
            org.junit.platform.commons;

    exports com.hedera.node.app.service.networkadmin.impl.test to
            org.mockito;
}
