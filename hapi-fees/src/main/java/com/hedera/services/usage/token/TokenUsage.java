package com.hedera.services.usage.token;

import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.UsageProperties;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.token.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public abstract class TokenUsage<T extends TokenUsage<T>> {
	protected static final int AMOUNT_REPR_BYTES = 8;

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

	protected void addAmountBpt() {
		usageEstimator.addBpt(AMOUNT_REPR_BYTES);
	}

	protected void addAccountBpt() {
		usageEstimator.addBpt(BASIC_ENTITY_ID_SIZE);
	}

	protected long refBpt(TokenRef ref) {
		return ref.hasTokenId() ? BASIC_ENTITY_ID_SIZE : ref.getSymbolBytes().size();
	}

	protected void addRefBpt(TokenRef ref) {
		usageEstimator.addBpt(refBpt(ref));
	}

	protected void addNetworkRecordRb(long rb) {
		usageEstimator.addNetworkRbs(rb * usageProperties.legacyReceiptStorageSecs());
	}

	protected void addRecordRb(long rb) {
		usageEstimator.addRbs(rb * usageProperties.legacyReceiptStorageSecs());
	}

	protected void addTransfersRecordRb(int numTokens, int numTransfers) {
		addRecordRb(tokenEntitySizes.bytesUsedToRecordTransfers(numTokens, numTransfers));
	}

	public T novelRelLasting(long secs) {
		usageEstimator.addRbs(tokenEntitySizes.bytesUsedPerAccountRelationship() * secs);
		return self();
	}
}
