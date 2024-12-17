open module com.swirlds.base.test.fixtures {
    exports com.swirlds.base.test.fixtures.context;
    exports com.swirlds.base.test.fixtures.time;
    exports com.swirlds.base.test.fixtures.io;
    exports com.swirlds.base.test.fixtures.util;
    exports com.swirlds.base.test.fixtures.concurrent;

    requires transitive com.swirlds.base;
    requires transitive org.junit.jupiter.api;
    requires jakarta.inject;
    requires org.assertj.core;
    requires static transitive com.github.spotbugs.annotations;
}
