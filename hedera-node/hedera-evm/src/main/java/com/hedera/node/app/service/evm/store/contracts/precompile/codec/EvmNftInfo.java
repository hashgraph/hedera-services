package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import org.hyperledger.besu.datatypes.Address;

public class EvmNftInfo {

  private long serialNumber;
  private Address account;
  private long creationTime;
  private byte[] metadata;
  private Address spender;

  public EvmNftInfo() {
  }


  public EvmNftInfo(long serialNumber, Address account, long creationTime, byte[] metadata,
      Address spender) {
    this.serialNumber = serialNumber;
    this.account = account;
    this.creationTime = creationTime;
    this.metadata = metadata;
    this.spender = spender;
  }

  public long getSerialNumber() {
    return serialNumber;
  }

  public Address getAccount() {
    return account;
  }

  public long getCreationTime() {
    return creationTime;
  }

  public byte[] getMetadata() {
    return metadata;
  }

  public Address getSpender() {
    return spender;
  }
}
