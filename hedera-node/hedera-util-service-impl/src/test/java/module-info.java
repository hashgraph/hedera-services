module com.hedera.node.app.service.util.impl.test {
    requires com.hedera.node.app.service.util;
    requires com.hedera.node.app.service.util.impl;
    requires org.junit.jupiter.api;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires com.hedera.pbj.runtime;

    opens com.hedera.node.app.service.util.impl.test to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.util.impl.test.handlers to
            org.junit.platform.commons,
            org.mockito;
}
