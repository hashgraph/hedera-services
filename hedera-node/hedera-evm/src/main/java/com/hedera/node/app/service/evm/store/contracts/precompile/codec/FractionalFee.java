package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import org.hyperledger.besu.datatypes.Address;

public class FractionalFee {

  private long numerator;
  private long denominator;
  private long getMinimumAmount;
  private long getMaximumAmount;
  private boolean netOfTransfers;
  private Address feeCollector;


  public FractionalFee(long numerator, long denominator, long getMinimumAmount,
      long getMaximumAmount, boolean netOfTransfers, Address feeCollector) {
    this.numerator = numerator;
    this.denominator = denominator;
    this.getMinimumAmount = getMinimumAmount;
    this.getMaximumAmount = getMaximumAmount;
    this.netOfTransfers = netOfTransfers;
    this.feeCollector = feeCollector;
  }
}
