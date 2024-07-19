/**
 * Provides a set of generic functions loading native libraries in different system architectures when packaged
 * in a jar using a predefined organization, so they can be accessed with JNI.
 */
module com.hedera.nativesupport {
    exports com.hedera.nativesupport;

    requires static transitive com.github.spotbugs.annotations;
}
