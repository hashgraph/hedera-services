package com.hedera.services.evm.store.contracts;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class MockEntityAccess implements HederaEvmEntityAccess{

  @Override
  public boolean isUsable(Address address) {
    return false;
  }

  @Override
  public long getBalance(Address address) {
    return 0;
  }

  @Override
  public boolean isTokenAccount(Address address) {
    return false;
  }

  @Override
  public ByteString alias(Address address) {
    return null;
  }

  @Override
  public boolean isExtant(Address address) {
    return false;
  }

  @Override
  public Bytes getStorage(Address address, Bytes key) {
    return Bytes.EMPTY;
  }

  @Override
  public Bytes fetchCodeIfPresent(Address address) {
    return null;
  }
}
