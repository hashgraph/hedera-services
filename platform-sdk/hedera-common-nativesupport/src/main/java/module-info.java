/**
 * Provides a set of generic functions loading native libraries in different system architectures when packaged
 * in a jar using a predefined organization, so they can be accessed with JNI.
 */
module com.hedera.common.nativesupport {
    exports com.hedera.common.nativesupport;

    requires static transitive com.github.spotbugs.annotations;
}
