open module com.swirlds.base.test.fixtures {
    exports com.swirlds.base.test.fixtures.context;
    exports com.swirlds.base.test.fixtures.time;
    exports com.swirlds.base.test.fixtures.io;
    exports com.swirlds.base.test.fixtures.util;
    exports com.swirlds.base.test.fixtures.concurrent;
    exports com.swirlds.base.test.fixtures.context.internal to
            org.junit.platform.commons;
    exports com.swirlds.base.test.fixtures.io.internal to
            org.junit.platform.commons;
    exports com.swirlds.base.test.fixtures.concurrent.internal to
            org.junit.platform.commons;

    requires transitive com.swirlds.base;
    requires transitive org.junit.jupiter.api;
    requires static com.github.spotbugs.annotations;
    requires com.swirlds.common; // Should be removed in future
    requires jakarta.inject;
}
