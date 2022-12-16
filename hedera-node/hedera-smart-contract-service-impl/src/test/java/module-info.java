module com.hedera.node.app.service.contract.impl.test {
    requires com.hedera.node.app.service.contract;
    requires com.hedera.node.app.service.contract.impl;
    requires org.junit.jupiter.api;

    opens com.hedera.node.app.service.contract.impl.test to
            org.junit.platform.commons;
}
