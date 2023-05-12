package com.hedera.node.app.service.contract.impl.state;


import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;

import java.util.NavigableMap;

/**
 * An {@link Account} implementation that provides access to the properties of the contract or
 * account with the given entity number, relative to the given {@link HederaState}.
 */
public class HederaAccount implements Account {
    private final long number;
    private final HederaState state;

    public HederaAccount(final long number, @NonNull final HederaState state) {
        this.number = number;
        this.state = state;
    }

    @Override
    public Address getAddress() {
        return null;
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
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(Bytes32 startKeyHash, int limit) {
        return null;
    }
}
