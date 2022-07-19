package com.hedera.services.bdd.suites.utils.contracts.precompile;

import org.hyperledger.besu.datatypes.Address;

public record Expiry(long second, Address autoRenewAccount, long autoRenewPeriod) {}