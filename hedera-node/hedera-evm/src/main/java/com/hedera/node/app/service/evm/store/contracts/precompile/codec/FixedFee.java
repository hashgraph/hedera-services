package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import org.hyperledger.besu.datatypes.Address;

public class FixedFee {

  private long amount;
  private Address denominatingTokenId;
  private boolean useHbarsForPayment;
  private boolean useCurrentTokenForPayment;
  private Address feeCollector;


  public FixedFee(long amount, Address denominatingTokenId, boolean useHbarsForPayment,
      boolean useCurrentTokenForPayment, Address feeCollector) {
    this.amount = amount;
    this.denominatingTokenId = denominatingTokenId;
    this.useHbarsForPayment = useHbarsForPayment;
    this.useCurrentTokenForPayment = useCurrentTokenForPayment;
    this.feeCollector = feeCollector;
  }
}
