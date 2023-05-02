module com.swirlds.common.test.fixtures {
    exports com.swirlds.common.test.fixtures;
    exports com.swirlds.common.test.fixtures.config;
    exports com.swirlds.common.test.fixtures.context;

    requires com.swirlds.common;
    requires com.swirlds.config;
    requires static com.github.spotbugs.annotations;
}
