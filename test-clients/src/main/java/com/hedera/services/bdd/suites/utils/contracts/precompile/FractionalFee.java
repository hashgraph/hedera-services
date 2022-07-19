package com.hedera.services.bdd.suites.utils.contracts.precompile;

import org.hyperledger.besu.datatypes.Address;

public record FractionalFee(
    long numerator,
    long denominator,
    long minimumAmount,
    long maximumAmount,
    boolean netOfTransfers,
    Address feeCollector) {}
