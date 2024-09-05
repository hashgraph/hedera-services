/**
 * This library implements the Groth21 TSS-specific primitives.
 * TSS (Threshold Signature Scheme): A cryptographic signing scheme in which a minimum number of parties (reconstruction threshold) must collaborate
 *   to produce an aggregate signature that can be used to sign messages and an aggregate public key that can be used to verify that signature.
 */
module com.hedera.cryptography.tss {
    requires transitive com.hedera.cryptography.pairings.signatures;
    requires static transitive com.github.spotbugs.annotations;

    exports com.hedera.cryptography.tss.api;
}
