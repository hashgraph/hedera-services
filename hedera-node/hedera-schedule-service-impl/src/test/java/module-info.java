module com.hedera.node.app.service.schedule.impl.test {
    requires com.hedera.node.app.service.token;
    requires org.junit.jupiter.api;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.google.protobuf;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.app.service.scheduled;
    requires com.hedera.node.app.service.schedule.impl;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires com.hedera.node.app.service.mono.testFixtures;

    opens com.hedera.node.app.service.schedule.impl.test to
            org.junit.platform.commons,
            org.mockito;

    exports com.hedera.node.app.service.schedule.impl.test to
            org.mockito;
}
