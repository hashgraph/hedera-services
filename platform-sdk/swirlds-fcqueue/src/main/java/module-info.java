module com.swirlds.fcqueue {
    exports com.swirlds.fcqueue;

    requires transitive com.swirlds.common;
    requires transitive com.swirlds.metrics.api;
    requires static transitive com.github.spotbugs.annotations;
}
