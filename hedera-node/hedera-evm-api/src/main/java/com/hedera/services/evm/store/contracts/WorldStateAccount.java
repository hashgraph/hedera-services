package com.hedera.services.evm.store.contracts;


import java.util.NavigableMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;

public class WorldStateAccount implements Account {
  private static final Code EMPTY_CODE =
      Code.createLegacyCode(Bytes.EMPTY, Hash.hash(Bytes.EMPTY));

  private final Wei balance;
  private final Address address;
  private final AbstractCodeCache codeCache;
  private final HederaEvmEntityAccess entityAccess;

  public WorldStateAccount(
      final Address address,
      final Wei balance,
      final AbstractCodeCache codeCache,
      final HederaEvmEntityAccess entityAccess) {
    this.balance = balance;
    this.address = address;
    this.codeCache = codeCache;
    this.entityAccess = entityAccess;
  }

  @Override
  public Address getAddress() {
    return address;
  }

  @Override
  public Hash getAddressHash() {
    return Hash.EMPTY;
  }

  @Override
  public long getNonce() {
    return 0;
  }

  @Override
  public Wei getBalance() {
    return balance;
  }

  @Override
  public Bytes getCode() {
    return getCodeInternal().getBytes();
  }

  @Override
  public boolean hasCode() {
    return !getCode().isEmpty();
  }

  @Override
  public Hash getCodeHash() {
    return getCodeInternal().getCodeHash();
  }

  @Override
  public UInt256 getStorageValue(final UInt256 key) {
    return UInt256.fromBytes(entityAccess.getStorage(address, key));
  }

  @Override
  public UInt256 getOriginalStorageValue(final UInt256 key) {
    return getStorageValue(key);
  }

  @Override
  public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(
      final Bytes32 startKeyHash, final int limit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return "AccountState"
        + "{"
        + "address="
        + getAddress()
        + ", "
        + "nonce="
        + getNonce()
        + ", "
        + "balance="
        + getBalance()
        + ", "
        + "codeHash="
        + getCodeHash()
        + ", "
        + "}";
  }

  private Code getCodeInternal() {
    final var code = codeCache.getIfPresent(address);
    return (code == null) ? EMPTY_CODE : code;
  }
}
