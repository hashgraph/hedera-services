module com.swirlds.crypto.tss {
    requires static transitive com.github.spotbugs.annotations;

    exports com.swirlds.crypto.tss;
    exports com.swirlds.crypto.signaturescheme.api;
    exports com.swirlds.crypto.pairings.api;
}
