package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

public class CustomFee {

  private FixedFee fixedFee;
  private FractionalFee fractionalFee;
  private RoyaltyFee royaltyFee;


  public void setFixedFee(
      FixedFee fixedFee) {
    this.fixedFee = fixedFee;
  }

  public void setFractionalFee(
      FractionalFee fractionalFee) {
    this.fractionalFee = fractionalFee;
  }

  public void setRoyaltyFee(
      RoyaltyFee royaltyFee) {
    this.royaltyFee = royaltyFee;
  }
}
