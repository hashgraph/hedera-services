package com.hedera.services.fees.charging;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.contracts.KvUsageInfo;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public interface StorageFeeCharging {
  void chargeStorageFees(
      long numTotalKvPairs,
      Map<AccountID, KvUsageInfo> newUsageInfos,
      TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts);
}
