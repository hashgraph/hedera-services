package com.hedera.services.evm.store.contracts;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;

public class EvmTrackingAccount {
    private long nonce;
    private  Wei balance;

    public EvmTrackingAccount(final long nonce, final Wei balance) {
        this.nonce = nonce;
        this.balance = balance;
    }

    public long getNonce() {
        return nonce;
    }

    public void setNonce(final long value) {
        this.nonce = value;
    }

    public Wei getBalance() {
        return balance;
    }

    public void setBalance(final Wei amount) {
        this.balance = amount;}
}
