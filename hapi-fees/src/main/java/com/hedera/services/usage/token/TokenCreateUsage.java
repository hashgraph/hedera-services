package com.hedera.services.usage.token;

import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.UsageProperties;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeBuilder;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.token.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hedera.services.usage.token.TokenUsageUtils.keySizeIfPresent;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public class TokenCreateUsage {
	static UsageProperties usageProperties = USAGE_PROPERTIES;
	static TokenEntitySizes tokenEntitySizes = TOKEN_ENTITY_SIZES;
	static EstimatorFactory estimatorFactory = TxnUsageEstimator::new;

	private final TransactionBody tokenCreationOp;
	private final TxnUsageEstimator usageEstimator;

	private TokenCreateUsage(TransactionBody tokenCreationOp, TxnUsageEstimator usageEstimator) {
		this.tokenCreationOp = tokenCreationOp;
		this.usageEstimator = usageEstimator;
	}

	public static TokenCreateUsage newEstimate(TransactionBody tokenCreationOp, SigUsage sigUsage) {
		return new TokenCreateUsage(tokenCreationOp, estimatorFactory.get(sigUsage, tokenCreationOp, ESTIMATOR_UTILS));
	}

	public FeeData get() {
		var op = tokenCreationOp.getTokenCreation();

		var baseSize = tokenEntitySizes.baseBytesUsed(op.getSymbol());
		baseSize += keySizeIfPresent(op, TokenCreateTransactionBody::hasKycKey, TokenCreateTransactionBody::getKycKey);
		baseSize += keySizeIfPresent(op, TokenCreateTransactionBody::hasWipeKey, TokenCreateTransactionBody::getWipeKey);
		baseSize += keySizeIfPresent(op, TokenCreateTransactionBody::hasAdminKey, TokenCreateTransactionBody::getAdminKey);
		baseSize += keySizeIfPresent(op, TokenCreateTransactionBody::hasSupplyKey, TokenCreateTransactionBody::getSupplyKey);
		baseSize += keySizeIfPresent(op, TokenCreateTransactionBody::hasFreezeKey, TokenCreateTransactionBody::getFreezeKey);
		if (op.hasAutoRenewAccount()) {
			baseSize += BASIC_ENTITY_ID_SIZE;
		}

		var lifetime = op.hasAutoRenewAccount()
				? op.getAutoRenewPeriod()
				: ESTIMATOR_UTILS.relativeLifetime(tokenCreationOp, op.getExpiry());
		usageEstimator.addBpt(baseSize);
		usageEstimator.addRbs(baseSize * lifetime);
		usageEstimator.addNetworkRbs(BASIC_ENTITY_ID_SIZE * usageProperties.legacyReceiptStorageSecs());

		return usageEstimator.get();
	}
}
