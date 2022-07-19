package com.hedera.services.bdd.suites.utils.contracts.precompile;

import org.hyperledger.besu.datatypes.Address;

public record FixedFee(
    long amount,
    Address tokenId,
    boolean useHbarsForPayment,
    boolean useCurrentTokenForPayment,
    Address feeCollector) {}