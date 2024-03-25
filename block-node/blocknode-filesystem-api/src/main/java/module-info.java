module com.hedera.storage.blocknode.filesystem.api {
    // Export packages with public interfaces to the world as needed.
    exports com.hedera.node.blocknode.filesystem.api;

    requires transitive com.hedera.node.hapi;
}