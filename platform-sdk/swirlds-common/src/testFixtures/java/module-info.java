open module com.swirlds.common.test.fixtures {
    exports com.swirlds.common.test.fixtures;
    exports com.swirlds.common.test.fixtures.benchmark;
    exports com.swirlds.common.test.fixtures.crypto;
    exports com.swirlds.common.test.fixtures.dummy;
    exports com.swirlds.common.test.fixtures.io;
    exports com.swirlds.common.test.fixtures.map;
    exports com.swirlds.common.test.fixtures.merkle.util;
    exports com.swirlds.common.test.fixtures.threading;
    exports com.swirlds.common.test.fixtures.set;
    exports com.swirlds.common.test.fixtures.stream;
    exports com.swirlds.common.test.fixtures.fcqueue;

    requires transitive com.swirlds.common;
    requires transitive com.swirlds.platform.core;
    requires com.swirlds.logging;
    requires lazysodium.java;
    requires org.apache.logging.log4j;
    requires org.junit.jupiter.api;
    requires static com.github.spotbugs.annotations;
}
