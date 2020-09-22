package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeBuilder;

import java.util.Optional;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.token.TokenUsageUtils.keySizeIfPresent;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public class TokenUpdateUsage extends TokenUsage<TokenUpdateUsage> {
	private int currentNameLen;
	private int currentSymbolLen;
	private long currentExpiry;
	private long currentMutableRb = 0;
	private boolean currentlyUsingAutoRenew = false;

	private TokenUpdateUsage(TransactionBody tokenUpdateOp, TxnUsageEstimator usageEstimator) {
		super(tokenUpdateOp, usageEstimator);
	}

	public static TokenUpdateUsage newEstimate(TransactionBody tokenUpdateOp, SigUsage sigUsage) {
		return new TokenUpdateUsage(tokenUpdateOp, estimatorFactory.get(sigUsage, tokenUpdateOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenUpdateUsage self() {
		return this;
	}

	public TokenUpdateUsage givenCurrentAdminKey(Optional<Key> adminKey) {
		adminKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateCurrentRb);
		return this;
	}

	public TokenUpdateUsage givenCurrentWipeKey(Optional<Key> wipeKey) {
		wipeKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateCurrentRb);
		return this;
	}

	public TokenUpdateUsage givenCurrentSupplyKey(Optional<Key> supplyKey) {
		supplyKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateCurrentRb);
		return this;
	}

	public TokenUpdateUsage givenCurrentFreezeKey(Optional<Key> freezeKey) {
		freezeKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateCurrentRb);
		return this;
	}

	public TokenUpdateUsage givenCurrentKycKey(Optional<Key> kycKey) {
		kycKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateCurrentRb);
		return this;
	}

	public TokenUpdateUsage givenCurrentName(String name) {
		currentNameLen = name.length();
		updateCurrentRb(currentNameLen);
		return this;
	}

	public TokenUpdateUsage givenCurrentSymbol(String symbol) {
		currentSymbolLen = symbol.length();
		updateCurrentRb(currentSymbolLen);
		return this;
	}

	public TokenUpdateUsage givenCurrentlyUsingAutoRenewAccount() {
		currentlyUsingAutoRenew = true;
		updateCurrentRb(BASIC_ENTITY_ID_SIZE);
		return this;
	}

	public TokenUpdateUsage givenCurrentExpiry(long expiry) {
		this.currentExpiry = expiry;
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenUpdate();

		long newMutableRb = 0;
		newMutableRb += keySizeIfPresent(op, TokenUpdateTransactionBody::hasKycKey, TokenUpdateTransactionBody::getKycKey);
		newMutableRb += keySizeIfPresent(op, TokenUpdateTransactionBody::hasWipeKey, TokenUpdateTransactionBody::getWipeKey);
		newMutableRb += keySizeIfPresent(op, TokenUpdateTransactionBody::hasAdminKey, TokenUpdateTransactionBody::getAdminKey);
		newMutableRb += keySizeIfPresent(op, TokenUpdateTransactionBody::hasSupplyKey, TokenUpdateTransactionBody::getSupplyKey);
		newMutableRb += keySizeIfPresent(op, TokenUpdateTransactionBody::hasFreezeKey, TokenUpdateTransactionBody::getFreezeKey);
		if (!removesAutoRenewAccount(op) && (currentlyUsingAutoRenew || op.hasAutoRenewAccount())) {
			newMutableRb += BASIC_ENTITY_ID_SIZE;
		}
		newMutableRb += (op.getName().length() > 0) ? op.getName().length() : currentNameLen;
		newMutableRb += (op.getSymbol().length() > 0) ? op.getSymbol().length() : currentSymbolLen;
		long newLifetime = ESTIMATOR_UTILS.relativeLifetime(tokenOp, Math.max(op.getExpiry(), currentExpiry));
		long rbsDelta = Math.max(0, newLifetime * (newMutableRb - currentMutableRb));
		if (rbsDelta > 0) {
			usageEstimator.addRbs(rbsDelta);
		}

		long txnBytes = newMutableRb + TokenUsageUtils.refBpt(op.getToken()) + noRbImpactBytes(op);
		usageEstimator.addBpt(txnBytes);
		addTransfersRecordRb(1, 2);

		return usageEstimator.get();
	}

	private long noRbImpactBytes(TokenUpdateTransactionBody op) {
		return ((op.getExpiry() > 0) ? AMOUNT_REPR_BYTES : 0) +
				((op.getAutoRenewPeriod() > 0) ? AMOUNT_REPR_BYTES : 0) +
				(op.hasTreasury() ? BASIC_ENTITY_ID_SIZE : 0) +
				(op.hasAutoRenewAccount() ? BASIC_ENTITY_ID_SIZE : 0);
	}

	private boolean removesAutoRenewAccount(TokenUpdateTransactionBody op) {
		return op.hasAutoRenewAccount() && op.getAutoRenewAccount().equals(AccountID.getDefaultInstance());
	}

	private void updateCurrentRb(long amount) {
		currentMutableRb += amount;
	}

}
