package com.hedera.services.usage.token;

import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.UsageProperties;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.token.TokenEntitySizes.TOKEN_ENTITY_SIZES;

public abstract class TokenUsage<T extends TokenUsage<T>> {
	static UsageProperties usageProperties = USAGE_PROPERTIES;
	static TokenEntitySizes tokenEntitySizes = TOKEN_ENTITY_SIZES;
	static EstimatorFactory estimatorFactory = TxnUsageEstimator::new;

	protected final TransactionBody tokenOp;
	protected final TxnUsageEstimator usageEstimator;

	abstract T self();

	protected TokenUsage(TransactionBody tokenOp, TxnUsageEstimator usageEstimator) {
		this.tokenOp = tokenOp;
		this.usageEstimator = usageEstimator;
	}

	protected void addRecordRb(long rb) {
		usageEstimator.addRbs(rb * usageProperties.legacyReceiptStorageSecs());
	}

	public T novelRelLasting(long secs) {
		usageEstimator.addRbs(tokenEntitySizes.bytesUsedPerAccountRelationship() * secs);
		return self();
	}
}
