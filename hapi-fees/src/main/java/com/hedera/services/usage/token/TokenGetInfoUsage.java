package com.hedera.services.usage.token;

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.fee.FeeBuilder;

import java.util.Optional;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.token.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_HEADER;

public class TokenGetInfoUsage {
	private long rb = 0;
	private final long customTb;

	private TokenGetInfoUsage(long customTb) {
		this.customTb = customTb;
	}

	public static TokenGetInfoUsage newEstimate(Query tokenQuery) {
		return new TokenGetInfoUsage(TokenUsageUtils.refBpt(tokenQuery.getTokenGetInfo().getToken()));
	}

	public TokenGetInfoUsage givenCurrentAdminKey(Optional<Key> adminKey) {
		adminKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateRb);
		return this;
	}

	public TokenGetInfoUsage givenCurrentWipeKey(Optional<Key> wipeKey) {
		wipeKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateRb);
		return this;
	}

	public TokenGetInfoUsage givenCurrentSupplyKey(Optional<Key> supplyKey) {
		supplyKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateRb);
		return this;
	}

	public TokenGetInfoUsage givenCurrentFreezeKey(Optional<Key> freezeKey) {
		freezeKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateRb);
		return this;
	}

	public TokenGetInfoUsage givenCurrentKycKey(Optional<Key> kycKey) {
		kycKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateRb);
		return this;
	}

	public TokenGetInfoUsage givenCurrentName(String name) {
		updateRb(name.length());
		return this;
	}

	public TokenGetInfoUsage givenCurrentSymbol(String symbol) {
		updateRb(symbol.length());
		return this;
	}

	public TokenGetInfoUsage givenCurrentlyUsingAutoRenewAccount() {
		updateRb(BASIC_ENTITY_ID_SIZE);
		return this;
	}

	public FeeData get() {
		long bpt = BASIC_QUERY_HEADER + customTb;
		long bpr = TOKEN_ENTITY_SIZES.fixedBytesUsed() + rb;
		var usage = FeeComponents.newBuilder()
				.setBpt(bpt)
				.setBpr(bpr)
				.build();
		return ESTIMATOR_UTILS.withDefaultQueryPartitioning(usage);
	}

	private void updateRb(long amount) {
		rb += amount;
	}
}
