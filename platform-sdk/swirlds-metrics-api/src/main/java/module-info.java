module com.swirlds.metrics.api {
    exports com.swirlds.metrics.api;
    exports com.swirlds.metrics.api.snapshot;

    requires transitive com.swirlds.base;
    requires static com.github.spotbugs.annotations;
}
