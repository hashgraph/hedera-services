package com.hedera.services.store.contracts;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

public class HederaUpdateTrackingAccount<A extends Account> extends UpdateTrackingAccount  {

    public HederaUpdateTrackingAccount(Account account) {
        super(account);
    }

    @Override
    public void setBalance(Wei value) {
        super.setBalance(value);
    }
}
