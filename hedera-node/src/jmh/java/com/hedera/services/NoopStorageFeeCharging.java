package com.hedera.services;

import com.hedera.services.fees.charging.StorageFeeCharging;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.tuweni.bytes.Bytes;

import java.util.Map;

public class NoopStorageFeeCharging implements StorageFeeCharging {
	@Override
	public void chargeStorageFees(
			final long numKvPairs,
			final Map<AccountID, Bytes> newBytecodes,
			final Map<AccountID, Integer> newUsageDeltas,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts
	) {
		// No-op
	}
}
