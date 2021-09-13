package com.hedera.services.store.contracts;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

public class HederaUpdateTrackingAccount extends UpdateTrackingAccount {

    public HederaUpdateTrackingAccount(Account account) {
        super(account);
    }

    @Override
    public void setBalance(Wei value) {
        super.setBalance(value);
    }

    //todo also implement incrementBalance and maybe write to state?git
    @Override
    public Wei decrementBalance(Wei value) {
        final Wei current = getBalance();
        if (current.compareTo(value) < 0) {
            throw new IllegalStateException(
                    String.format("Cannot remove %s wei from account, balance is only %s", value, current));
        }
        setBalance(current.subtract(value));
        return current;
    }
}
