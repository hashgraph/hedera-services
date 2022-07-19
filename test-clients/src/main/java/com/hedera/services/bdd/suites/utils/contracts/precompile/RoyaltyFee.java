package com.hedera.services.bdd.suites.utils.contracts.precompile;

import org.hyperledger.besu.datatypes.Address;

public record RoyaltyFee(
    long numerator,
    long denominator,
    long amount,
    Address tokenId,
    boolean useHbarsForPayment,
    Address feeCollector) {}