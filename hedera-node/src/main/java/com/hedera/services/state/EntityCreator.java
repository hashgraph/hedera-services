package com.hedera.services.state;

import com.hedera.services.ledger.HederaLedger;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;

public interface EntityCreator {
	void setLedger(HederaLedger ledger);
	void createExpiringPayerRecord(AccountID id, TransactionRecord record, long now);
	void createExpiringHistoricalRecord(AccountID id, TransactionRecord record, long now);
}
