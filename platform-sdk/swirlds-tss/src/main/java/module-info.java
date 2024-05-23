module com.swirlds.tss {
    exports com.swirlds.tss.api;

    requires transitive com.swirlds.pairings.api;
    requires static com.github.spotbugs.annotations;
}
