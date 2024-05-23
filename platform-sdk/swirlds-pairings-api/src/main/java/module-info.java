module com.swirlds.pairings.api {
    uses com.swirlds.pairings.spi.BilinearPairingProvider;

    exports com.swirlds.pairings.api;
    exports com.swirlds.pairings.ecdh;
    exports com.swirlds.pairings.spi;
    exports com.swirlds.signaturescheme.api;

    requires static com.github.spotbugs.annotations;
}
