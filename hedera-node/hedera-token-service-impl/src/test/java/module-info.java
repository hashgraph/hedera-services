module com.hedera.node.app.service.token.impl.test {
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.service.token.impl;
    requires org.junit.jupiter.api;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.google.protobuf;
    requires com.hedera.node.app.service.mono;

    opens com.hedera.node.app.service.token.impl.test to
            org.junit.platform.commons;
}
