/**
 * A map that implements the FastCopyable interface.
 */
open module com.swirlds.virtualmap {
    // MerkleDb
    exports com.swirlds.merkledb;
    exports com.swirlds.merkledb.collections;
    exports com.swirlds.merkledb.config;
    exports com.swirlds.merkledb.files;
    exports com.swirlds.merkledb.files.hashmap;
    exports com.swirlds.merkledb.serialize;
    exports com.swirlds.merkledb.utilities;
    // VirtualMap
    exports com.swirlds.virtualmap;
    exports com.swirlds.virtualmap.datasource;
    // Currently, exported only for tests.
    exports com.swirlds.virtualmap.internal.merkle;
    exports com.swirlds.virtualmap.config;

    // Testing-only exports
    exports com.swirlds.virtualmap.internal to
            com.swirlds.virtualmap.test.fixtures;
    exports com.swirlds.virtualmap.internal.cache to
            com.swirlds.virtualmap.test.fixtures;

    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.metrics.api;
    requires transitive com.hedera.pbj.runtime;
    requires com.swirlds.base;
    requires com.swirlds.config.extensions;
    requires com.swirlds.logging;
    requires java.management;
    requires jdk.management;
    requires jdk.unsupported;
    requires org.apache.logging.log4j;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires static com.github.spotbugs.annotations;

    uses com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
}
