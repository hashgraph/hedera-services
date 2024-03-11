module com.hedera.storage.blocknode.core {
    // Selectively export non-public packages to the test module.
    exports com.hedera.node.blocknode.core to
            com.hedera.storage.blocknode.core.test;

    // Require the modules needed for compilation.
    requires com.hedera.storage.blocknode.config;
    requires com.swirlds.config.api;
    requires io.grpc;
    requires org.apache.logging.log4j;
    // Require modules which are needed for compilation and should be available to all modules that depend on this
    // module (including tests and other source sets).
    requires static com.github.spotbugs.annotations;
}
