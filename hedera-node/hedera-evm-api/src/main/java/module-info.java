/** Provides the core interfaces for the Hedera EVM implementation. */
module com.hedera.services.evm {
    requires org.hyperledger.besu.evm;
    requires org.hyperledger.besu.datatypes;
    requires com.google.protobuf;
    requires hedera.protobuf.java.api;
    requires org.apache.commons.lang3;
    requires tuweni.bytes;

    exports com.hedera.services.evm;
    exports com.hedera.services.evm.contracts.execution;
    exports com.hedera.services.evm.store.contracts;
    exports com.hedera.services.evm.store.models;
}
