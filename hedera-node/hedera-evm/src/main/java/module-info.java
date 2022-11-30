/** Provides the core interfaces for the Hedera EVM implementation. */
module com.hedera.node.app.service.evm {
    requires org.hyperledger.besu.evm;
    requires org.hyperledger.besu.datatypes;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires tuweni.bytes;
    requires javax.inject;
    requires com.swirlds.common;
    requires com.google.common;
    requires tuweni.units;
    requires com.github.benmanes.caffeine;
    requires com.google.protobuf;
    requires static com.github.spotbugs.annotations;
    requires headlong;

    exports com.hedera.node.app.service.evm.store.contracts.utils;
    exports com.hedera.node.app.service.evm.contracts.execution;
    exports com.hedera.node.app.service.evm.store.contracts;
    exports com.hedera.node.app.service.evm.store.models;
    exports com.hedera.node.app.service.evm;
}
