module com.hedera.storage.blocknode.util {
    // Export the packages that should be available to other modules.
    exports com.hedera.node.blocknode.util;

    requires transitive com.hedera.node.hapi;
}
