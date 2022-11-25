module com.hedera.node.app.service.scheduled.impl.test {
    requires com.hedera.node.app.service.scheduled;
    requires com.hedera.node.app.service.scheduled.impl;
    requires org.junit.jupiter.api;

    opens com.hedera.node.app.service.scheduled.impl.test to
            org.junit.platform.commons;
}
