/**
 * A map that implements the FastCopyable interface.
 */
open module com.swirlds.merkle {
    exports com.swirlds.merkle.map;
    exports com.swirlds.merkle.tree;
    exports com.swirlds.merkle.tree.internal to
            com.swirlds.merkle.testing;
    exports com.swirlds.merkle.map.internal;

    requires transitive com.swirlds.common;
    requires transitive com.swirlds.fchashmap;
    requires static com.github.spotbugs.annotations;
}
