module com.hedera.storage.blocknode.state {
    // Export the packages that should be available to other modules.
    exports com.hedera.node.blocknode.state;

    // Require the modules needed for compilation.
    requires com.hedera.storage.blocknode.core.spi;

    // Require modules which are needed for compilation and should be available to all modules that depend on this
    // module (including tests and other source sets
    requires transitive com.swirlds.platform.core;
}
