package com.hedera.services.store.contracts;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

public class HederaUpdateTrackingAccount extends UpdateTrackingAccount<HederaWorldState.WorldStateAccount> {

    public HederaUpdateTrackingAccount(HederaWorldState.WorldStateAccount account) {
        super(account);
    }

    @Override
    public void setBalance(Wei value) {
        super.setBalance(value);
    }
}
