open module com.hedera.node.app.service.file.impl.test {
    requires com.hedera.node.app.service.file;
    requires com.hedera.node.app.service.file.impl;
    requires org.junit.jupiter.api;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires com.hedera.node.app.service.mono.testFixtures;
    requires com.hedera.pbj.runtime;
    requires com.hedera.node.app.spi.fixtures;
    requires com.hedera.node.app.service.mono;
    requires org.assertj.core;
    requires com.hedera.node.app.service.token;
    requires com.swirlds.common;
    requires org.apache.commons.lang3;
    requires com.github.spotbugs.annotations;
}
