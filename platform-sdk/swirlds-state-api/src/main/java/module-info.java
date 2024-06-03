module com.swirlds.state.api {
    exports com.swirlds.state;
    exports com.swirlds.state.spi;
    exports com.swirlds.state.spi.metrics;

    requires com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
}
