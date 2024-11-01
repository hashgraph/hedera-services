module com.swirlds.state.api {
    exports com.swirlds.state;
    exports com.swirlds.state.spi;
    exports com.swirlds.state.spi.metrics;

    requires transitive com.swirlds.common;
    requires com.hedera.pbj.runtime;
    requires static transitive com.github.spotbugs.annotations;
}
