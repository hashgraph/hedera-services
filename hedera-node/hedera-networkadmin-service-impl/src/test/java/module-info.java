module com.hedera.node.app.service.networkadmin.impl.test {
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.hedera.node.app.service.networkadmin.impl;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.spi.test.fixtures;
    requires com.swirlds.fcqueue;
    requires org.junit.jupiter.api;
    requires org.mockito.junit.jupiter;
    requires org.mockito;

    opens com.hedera.node.app.service.networkadmin.impl.test to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.networkadmin.impl.test.handlers to
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
