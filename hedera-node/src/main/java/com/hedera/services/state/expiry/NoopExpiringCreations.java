package com.hedera.services.state.expiry;

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.EntityCreator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;

public enum NoopExpiringCreations implements EntityCreator {
	NOOP_EXPIRING_CREATIONS;

	@Override
	public void setLedger(HederaLedger ledger) { }

	@Override
	public void createExpiringPayerRecord(AccountID id, TransactionRecord record, long now) { }

	@Override
	public void createExpiringHistoricalRecord(AccountID id, TransactionRecord record, long now) { }
}
