open module com.swirlds.common.test {
    exports com.swirlds.common.test;
    exports com.swirlds.common.test.io;
    exports com.swirlds.common.test.state;
    exports com.swirlds.common.test.merkle.util;
    exports com.swirlds.common.test.merkle.dummy;
    exports com.swirlds.common.test.dummy;
    exports com.swirlds.common.test.benchmark;
    exports com.swirlds.common.test.set;
    exports com.swirlds.common.test.map;
    exports com.swirlds.common.test.threading;
    exports com.swirlds.common.test.crypto;
    exports com.swirlds.common.test.fcqueue;

    requires com.swirlds.test.framework;
    requires com.swirlds.common;
    requires org.bouncycastle.provider;
    requires org.junit.jupiter.api;
    requires org.apache.commons.lang3;
    requires java.scripting;
    requires org.apache.logging.log4j;
    requires com.fasterxml.jackson.databind;
    requires lazysodium.java;
    requires static com.github.spotbugs.annotations;
}
