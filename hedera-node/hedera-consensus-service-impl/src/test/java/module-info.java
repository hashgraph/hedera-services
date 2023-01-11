module com.hedera.node.app.service.consensus.impl.test {
    requires com.hedera.node.app.service.consensus;
    requires com.hedera.node.app.service.consensus.impl;
    requires org.junit.jupiter.api;

    opens com.hedera.node.app.service.consensus.impl.test to
            org.junit.platform.commons;
}
