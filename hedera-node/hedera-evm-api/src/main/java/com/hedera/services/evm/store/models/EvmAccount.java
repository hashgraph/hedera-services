package com.hedera.services.evm.store.models;

import java.util.NavigableMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;

public class EvmAccount implements Account {

  private final Address address;

  public EvmAccount(Address address) {
    this.address = address;
  }

  @Override
  public Address getAddress() {
    return address;
  }

  @Override
  public Hash getAddressHash() {
    return null;
  }

  @Override
  public long getNonce() {
    return 0;
  }

  @Override
  public Wei getBalance() {
    return null;
  }

  @Override
  public Bytes getCode() {
    return null;
  }

  @Override
  public Hash getCodeHash() {
    return null;
  }

  @Override
  public UInt256 getStorageValue(UInt256 key) {
    return null;
  }

  @Override
  public UInt256 getOriginalStorageValue(UInt256 key) {
    return null;
  }

  @Override
  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(Bytes32 startKeyHash,
      int limit) {
    return null;
  }
}