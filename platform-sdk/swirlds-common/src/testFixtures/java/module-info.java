module com.swirlds.common.test.fixtures {
    exports com.swirlds.common.test.fixtures;
    exports com.swirlds.common.test.fixtures.crypto;
    exports com.swirlds.common.test.fixtures.io;
    exports com.swirlds.common.test.fixtures.merkle.util;

    requires com.swirlds.common;
    requires org.junit.jupiter.api;
    requires lazysodium.java;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
