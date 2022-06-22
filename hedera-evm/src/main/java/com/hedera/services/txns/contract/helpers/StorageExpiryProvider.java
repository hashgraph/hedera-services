package com.hedera.services.txns.contract.helpers;

/**
 * Provides different types of OracleProvider implementations
 */
public interface StorageExpiryProvider {

    OracleProvider hapiCreationOracle(long hapiExpiry);

    OracleProvider hapiCallOracle();

    OracleProvider hapiStaticCallOracle();
}
