package com.hedera.services.evm.store.contracts;

import com.hedera.services.evm.accounts.AccountAccessor;

public class HederaEvmStackedWorldStateUpdater extends AbstractLedgerEvmWorldUpdater{

  public HederaEvmStackedWorldStateUpdater(
      AccountAccessor accountAccessor) {
    super(accountAccessor);
  }
}
