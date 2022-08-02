package com.hedera.services.mocks;

import com.hedera.services.fees.charging.StorageFeeCharging;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.contracts.KvUsageInfo;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Map;

public class NoopStorageFeeCharging implements StorageFeeCharging {

  @Override
  public void chargeStorageFees(
      long numTotalKvPairs,
      Map<Long, KvUsageInfo> newUsageInfos,
      TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts) {
    // Intentional no-op
  }
}
