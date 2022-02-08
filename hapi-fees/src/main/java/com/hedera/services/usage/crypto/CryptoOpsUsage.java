package com.hedera.services.usage.crypto;

/*-
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.QueryUsage;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hedera.services.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

@Singleton
public class CryptoOpsUsage {
	private static final long LONG_BASIC_ENTITY_ID_SIZE = BASIC_ENTITY_ID_SIZE;
	private static final long LONG_ACCOUNT_AMOUNT_BYTES = USAGE_PROPERTIES.accountAmountBytes();

	static final long CREATE_SLOT_MULTIPLIER = 1228;
	static final long UPDATE_SLOT_MULTIPLIER = 24000;

	static EstimatorFactory txnEstimateFactory = TxnUsageEstimator::new;
	static Function<ResponseType, QueryUsage> queryEstimateFactory = QueryUsage::new;

	@Inject
	public CryptoOpsUsage() {
		// Default constructor
	}

	public void cryptoTransferUsage(
			SigUsage sigUsage,
			CryptoTransferMeta xferMeta,
			BaseTransactionMeta baseMeta,
			UsageAccumulator accumulator
	) {
		accumulator.resetForTransaction(baseMeta, sigUsage);

		final int tokenMultiplier = xferMeta.getTokenMultiplier();

		/* BPT calculations shouldn't include any custom fee payment usage */
		int totalXfers = baseMeta.numExplicitTransfers();
		int weightedTokensInvolved = tokenMultiplier * xferMeta.getNumTokensInvolved();
		int weightedTokenXfers = tokenMultiplier * xferMeta.getNumFungibleTokenTransfers();
		long incBpt = weightedTokensInvolved * LONG_BASIC_ENTITY_ID_SIZE;
		incBpt += (weightedTokenXfers + totalXfers) * LONG_ACCOUNT_AMOUNT_BYTES;
		incBpt += TOKEN_ENTITY_SIZES.bytesUsedForUniqueTokenTransfers(xferMeta.getNumNftOwnershipChanges());
		accumulator.addBpt(incBpt);

		totalXfers += xferMeta.getCustomFeeHbarTransfers();
		weightedTokenXfers += tokenMultiplier * xferMeta.getCustomFeeTokenTransfers();
		weightedTokensInvolved += tokenMultiplier * xferMeta.getCustomFeeTokensInvolved();
		long incRb = totalXfers * LONG_ACCOUNT_AMOUNT_BYTES;
		incRb += TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(
				weightedTokensInvolved,
				weightedTokenXfers,
				xferMeta.getNumNftOwnershipChanges());
		accumulator.addRbs(incRb * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	public FeeData cryptoInfoUsage(Query cryptoInfoReq, ExtantCryptoContext ctx) {
		var op = cryptoInfoReq.getCryptoGetInfo();

		var estimate = queryEstimateFactory.apply(op.getHeader().getResponseType());
		estimate.addTb(BASIC_ENTITY_ID_SIZE);
		long extraRb = 0;
		extraRb += ctx.currentMemo().getBytes(StandardCharsets.UTF_8).length;
		extraRb += getAccountKeyStorageSize(ctx.currentKey());
		if (ctx.currentlyHasProxy()) {
			extraRb += BASIC_ENTITY_ID_SIZE;
		}
		extraRb += ctx.currentNumTokenRels() * TOKEN_ENTITY_SIZES.bytesUsedPerAccountRelationship();
		estimate.addRb(CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() + extraRb);

		return estimate.get();
	}

	public long cryptoAutoRenewRb(ExtantCryptoContext ctx) {
		return CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr()
				+ ctx.currentNonBaseRb()
				+ ctx.currentNumTokenRels() * CRYPTO_ENTITY_SIZES.bytesInTokenAssocRepr();
	}

	public void cryptoUpdateUsage(final SigUsage sigUsage,
			final BaseTransactionMeta baseMeta,
			final CryptoUpdateMeta cryptoUpdateMeta,
			final ExtantCryptoContext ctx,
			final UsageAccumulator accumulator) {
		accumulator.resetForTransaction(baseMeta, sigUsage);

		accumulator.addBpt(cryptoUpdateMeta.getMsgBytesUsed());

		long newVariableBytes = 0;
		var newMemoSize = cryptoUpdateMeta.getMemoSize();
		newVariableBytes += newMemoSize != 0 ? newMemoSize : ctx.currentMemo().getBytes(StandardCharsets.UTF_8).length;
		var newKeyBytes = cryptoUpdateMeta.getKeyBytesUsed();
		newVariableBytes += newKeyBytes == 0 ? getAccountKeyStorageSize(ctx.currentKey()) : newKeyBytes;
		newVariableBytes += (cryptoUpdateMeta.hasProxy() || ctx.currentlyHasProxy()) ? BASIC_ENTITY_ID_SIZE : 0;

		long tokenRelBytes = ctx.currentNumTokenRels() * CRYPTO_ENTITY_SIZES.bytesInTokenAssocRepr();
		long sharedFixedBytes = CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() + tokenRelBytes;
		long newLifetime = ESTIMATOR_UTILS.relativeLifetime(
				cryptoUpdateMeta.getEffectiveNow(), cryptoUpdateMeta.getExpiry());
		long oldLifetime = ESTIMATOR_UTILS.relativeLifetime(
				cryptoUpdateMeta.getEffectiveNow(), ctx.currentExpiry());
		long rbsDelta = ESTIMATOR_UTILS.changeInBsUsage(
				cryptoAutoRenewRb(ctx),
				oldLifetime,
				sharedFixedBytes + newVariableBytes,
				newLifetime);
		if (rbsDelta > 0) {
			accumulator.addRbs(rbsDelta);
		}

		final var oldSlotsUsage = ctx.currentMaxAutomaticAssociations() * UPDATE_SLOT_MULTIPLIER;
		final var newSlotsUsage = cryptoUpdateMeta.hasMaxAutomaticAssociations()
				? cryptoUpdateMeta.getMaxAutomaticAssociations() * UPDATE_SLOT_MULTIPLIER
				: oldSlotsUsage;
		long slotRbsDelta = ESTIMATOR_UTILS.changeInBsUsage(oldSlotsUsage, oldLifetime, newSlotsUsage, newLifetime);
		if (slotRbsDelta > 0) {
			accumulator.addRbs(slotRbsDelta);
		}
	}

	public void cryptoCreateUsage(final SigUsage sigUsage,
			final BaseTransactionMeta baseMeta,
			final CryptoCreateMeta cryptoCreateMeta,
			final UsageAccumulator accumulator) {
		accumulator.resetForTransaction(baseMeta, sigUsage);

		var baseSize = cryptoCreateMeta.getBaseSize();

		var maxAutomaticTokenAssociations = cryptoCreateMeta.getMaxAutomaticAssociations();

		var lifeTime = cryptoCreateMeta.getLifeTime();

		if (maxAutomaticTokenAssociations > 0) {
			baseSize += INT_SIZE;
		}

		/* Variable bytes plus two additional longs for balance and auto-renew period;
			plus a boolean for receiver sig required. */
		accumulator.addBpt(baseSize + 2 * LONG_SIZE + BOOL_SIZE);
		accumulator.addRbs((CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() + baseSize) * lifeTime);
		accumulator.addRbs(maxAutomaticTokenAssociations * lifeTime * CREATE_SLOT_MULTIPLIER);
		accumulator.addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	public void cryptoApproveAllowanceUsage(final SigUsage sigUsage,
			final BaseTransactionMeta baseMeta,
			final CryptoAllowanceMeta cryptoApproveMeta,
			final ExtantCryptoContext ctx,
			final UsageAccumulator accumulator) {
		accumulator.resetForTransaction(baseMeta, sigUsage);
		accumulator.addBpt(cryptoApproveMeta.getMsgBytesUsed());

		final long lifeTime = ESTIMATOR_UTILS.relativeLifetime(
				cryptoApproveMeta.getEffectiveNow(), ctx.currentExpiry());
		accumulator.addRbs(cryptoApproveMeta.getMsgBytesUsed() * lifeTime);
	}

	public void cryptoAdjustAllowanceUsage(final SigUsage sigUsage,
			final BaseTransactionMeta baseMeta,
			final CryptoAllowanceMeta cryptoAdjustMeta,
			final ExtantCryptoContext ctx,
			final UsageAccumulator accumulator) {
		accumulator.resetForTransaction(baseMeta, sigUsage);
		accumulator.addBpt(cryptoAdjustMeta.getMsgBytesUsed());

		var newVariableSerialBytes = 0;
		newVariableSerialBytes += cryptoAdjustMeta.getAggregatedNftAllowancesWithSerials() * LONG_SIZE;

		final long sharedFixedBytes = CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr();

		final long lifeTime = ESTIMATOR_UTILS.relativeLifetime(
				cryptoAdjustMeta.getEffectiveNow(), ctx.currentExpiry());
		final long rbsDelta = ESTIMATOR_UTILS.changeInBsUsage(
				cryptoAutoRenewRb(ctx),
				lifeTime,
				sharedFixedBytes + newVariableSerialBytes,
				lifeTime);
		if (rbsDelta > 0) {
			accumulator.addRbs(rbsDelta);
		}
	}
}
