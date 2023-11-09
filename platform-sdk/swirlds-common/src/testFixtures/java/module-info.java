open module com.swirlds.common.test.fixtures {
    exports com.swirlds.common.test.fixtures;
    exports com.swirlds.common.test.fixtures.crypto;
    exports com.swirlds.common.test.fixtures.io;
    exports com.swirlds.common.test.fixtures.merkle.util;
    exports com.swirlds.common.test.fixtures.threading;
    exports com.swirlds.common.test.fixtures.stream;
    exports com.swirlds.common.test.fixtures.fcqueue;

    requires transitive com.swirlds.common;
    requires lazysodium.java;
    requires org.apache.logging.log4j;
    requires org.junit.jupiter.api;
    requires static com.github.spotbugs.annotations;
}
