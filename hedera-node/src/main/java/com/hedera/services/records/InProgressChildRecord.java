package com.hedera.services.records;

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class InProgressChildRecord {
	private final int sourceId;
	private final TransactionBody.Builder syntheticBody;
	private final ExpirableTxnRecord.Builder recordBuilder;

	public InProgressChildRecord(
			final int sourceId,
			final TransactionBody.Builder syntheticBody,
			final ExpirableTxnRecord.Builder recordBuilder
	) {
		this.sourceId = sourceId;
		this.syntheticBody = syntheticBody;
		this.recordBuilder = recordBuilder;
	}

	public int getSourceId() {
		return sourceId;
	}

	public TransactionBody.Builder getSyntheticBody() {
		return syntheticBody;
	}

	public ExpirableTxnRecord.Builder getRecordBuilder() {
		return recordBuilder;
	}
}
