module com.hedera.node.app.service.schedule.impl.test {
    requires org.junit.jupiter.api;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.google.protobuf;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.service.schedule.impl;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires com.hedera.node.app.service.mono.testFixtures;
    requires org.apache.commons.lang3;
    requires org.apache.commons.codec;

    opens com.hedera.node.app.service.schedule.impl.test to
            org.junit.platform.commons,
            org.mockito;

    exports com.hedera.node.app.service.schedule.impl.test to
            org.mockito;

    opens com.hedera.node.app.service.schedule.impl.test.handlers to
            org.junit.platform.commons,
            org.mockito;
}
