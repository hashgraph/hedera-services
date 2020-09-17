package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.token.TokenUsageUtils.keySizeIfPresent;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public class TokenCreateUsage extends TokenUsage<TokenCreateUsage> {
	private TokenCreateUsage(TransactionBody tokenCreationOp, TxnUsageEstimator usageEstimator) {
		super(tokenCreationOp, usageEstimator);
	}

	public static TokenCreateUsage newEstimate(TransactionBody tokenCreationOp, SigUsage sigUsage) {
		return new TokenCreateUsage(tokenCreationOp, estimatorFactory.get(sigUsage, tokenCreationOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenCreateUsage self() {
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenCreation();

		var baseSize = tokenEntitySizes.baseBytesUsed(op.getSymbol(), op.getName());
		baseSize += keySizeIfPresent(op, TokenCreation::hasKycKey, TokenCreation::getKycKey);
		baseSize += keySizeIfPresent(op, TokenCreation::hasWipeKey, TokenCreation::getWipeKey);
		baseSize += keySizeIfPresent(op, TokenCreation::hasAdminKey, TokenCreation::getAdminKey);
		baseSize += keySizeIfPresent(op, TokenCreation::hasSupplyKey, TokenCreation::getSupplyKey);
		baseSize += keySizeIfPresent(op, TokenCreation::hasFreezeKey, TokenCreation::getFreezeKey);
		if (op.hasAutoRenewAccount()) {
			baseSize += BASIC_ENTITY_ID_SIZE;
		}
		var lifetime = op.hasAutoRenewAccount()
				? op.getAutoRenewPeriod()
				: ESTIMATOR_UTILS.relativeLifetime(tokenOp, op.getExpiry());

		usageEstimator.addBpt(baseSize);
		usageEstimator.addRbs(baseSize * lifetime);
		addNetworkRecordRb(BASIC_ENTITY_ID_SIZE);
		addTransfersRecordRb(1, 1);

		return usageEstimator.get();
	}
}
