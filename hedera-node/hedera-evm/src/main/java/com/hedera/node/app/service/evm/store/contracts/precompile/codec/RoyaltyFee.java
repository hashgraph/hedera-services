package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import org.hyperledger.besu.datatypes.Address;

public class RoyaltyFee {

  private long numerator;
  private long denominator;
  private long amount;
  private Address denominatingTokenId;
  private boolean useHbarsForPayment;
  private Address feeCollector;


  public RoyaltyFee(long numerator, long denominator, long amount, Address denominatingTokenId,
      boolean useHbarsForPayment, Address feeCollector) {
    this.numerator = numerator;
    this.denominator = denominator;
    this.amount = amount;
    this.denominatingTokenId = denominatingTokenId;
    this.useHbarsForPayment = useHbarsForPayment;
    this.feeCollector = feeCollector;
  }
}
