package com.hedera.services.store.contracts;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;

import java.util.NavigableMap;

public class EvmAccountImpl implements EvmAccount {

    private final Address address;
    private final Wei balance;
    private final Hash addressHash;
    private final Bytes code;

    public EvmAccountImpl (Address address, Wei balance) {
        this(address, balance, Bytes.EMPTY);
    }

    public EvmAccountImpl (Address address, Wei balance, Bytes code) {
        this.address = address;
        this.balance = balance;
        this.addressHash = Hash.hash(address);
        this.code = code;
    }

    @Override
    public MutableAccount getMutable() throws ModificationNotAllowedException {
        throw new ModificationNotAllowedException();
    }

    @Override
    public Address getAddress() {
        return address;
    }

    @Override
    public Hash getAddressHash() {
        return addressHash;
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
        return code;
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
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(Bytes32 startKeyHash, int limit) {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
