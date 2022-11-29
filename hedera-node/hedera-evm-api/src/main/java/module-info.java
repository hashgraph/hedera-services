/** Provides the core interfaces for the Hedera EVM implementation. */
module com.hedera.services.evm {
    requires org.hyperledger.besu.evm;
    requires org.hyperledger.besu.datatypes;
    requires org.hyperledger.besu.secp256k1;
    requires com.hedera.hashgraph.protobuf.java.api;
    requires tuweni.bytes;
    requires javax.inject;
    requires com.swirlds.common;
    requires com.google.common;
    requires tuweni.units;
    requires com.github.benmanes.caffeine;
    requires com.google.protobuf;
    requires org.apache.commons.lang3;
    requires org.bouncycastle.provider;
    requires com.sun.jna;
    requires static com.github.spotbugs.annotations;

    exports com.hedera.services.evm.store.contracts.utils;
    exports com.hedera.services.evm.contracts.execution;
    exports com.hedera.services.evm.contracts.execution.traceability;
    exports com.hedera.services.evm.store.contracts;
    exports com.hedera.services.evm.store.models;
    exports com.hedera.services.evm.accounts;
    exports com.hedera.services.evm;
    exports com.hedera.services.utils;
}
