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
import com.hederahashgraph.api.proto.java.TransactionBody;

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

	static EstimatorFactory txnEstimateFactory = TxnUsageEstimator::new;
	static Function<ResponseType, QueryUsage> queryEstimateFactory = QueryUsage::new;

	@Inject
	public CryptoOpsUsage() {
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
		int totalXfers = baseMeta.getNumExplicitTransfers();
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

	public FeeData cryptoUpdateUsage(TransactionBody cryptoUpdate, SigUsage sigUsage, ExtantCryptoContext ctx) {
		var op = cryptoUpdate.getCryptoUpdateAccount();

		long keyBytesUsed = op.hasKey() ? getAccountKeyStorageSize(op.getKey()) : 0;
		long msgBytesUsed = BASIC_ENTITY_ID_SIZE
				+ op.getMemo().getValueBytes().size()
				+ keyBytesUsed
				+ (op.hasExpirationTime() ? LONG_SIZE : 0)
				+ (op.hasAutoRenewPeriod() ? LONG_SIZE : 0)
				+ (op.hasProxyAccountID() ? BASIC_ENTITY_ID_SIZE : 0)
				+ (op.hasMaxAutomaticTokenAssociations() ? INT_SIZE : 0);
		var estimate = txnEstimateFactory.get(sigUsage, cryptoUpdate, ESTIMATOR_UTILS);
		estimate.addBpt(msgBytesUsed);

		long newVariableBytes = 0;
		newVariableBytes += !op.hasMemo()
				? ctx.currentMemo().getBytes(StandardCharsets.UTF_8).length
				: op.getMemo().getValueBytes().size();
		newVariableBytes += !op.hasKey() ? getAccountKeyStorageSize(ctx.currentKey()) : keyBytesUsed;
		newVariableBytes += (op.hasProxyAccountID() || ctx.currentlyHasProxy()) ? BASIC_ENTITY_ID_SIZE : 0;

		long tokenRelBytes = ctx.currentNumTokenRels() * CRYPTO_ENTITY_SIZES.bytesInTokenAssocRepr();
		long sharedFixedBytes = CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() + tokenRelBytes;
		long newLifetime = ESTIMATOR_UTILS.relativeLifetime(cryptoUpdate, op.getExpirationTime().getSeconds());
		long oldLifetime = ESTIMATOR_UTILS.relativeLifetime(cryptoUpdate, ctx.currentExpiry());
		long rbsDelta = ESTIMATOR_UTILS.changeInBsUsage(
				cryptoAutoRenewRb(ctx),
				oldLifetime,
				sharedFixedBytes + newVariableBytes,
				newLifetime);
		if (rbsDelta > 0) {
			estimate.addRbs(rbsDelta);
		}

		long maxAutoAssociationsDelta = op.hasMaxAutomaticTokenAssociations() ?
				((op.getMaxAutomaticTokenAssociations().getValue() * newLifetime)
						- (ctx.currentMaxAutomaticAssociations() * oldLifetime)) : 0L;

		if (maxAutoAssociationsDelta > 0) {
			/* 	A multiplier '27' is used here to match the cost of each auto-association slot with cost for
			one additional association in a tokenAssociate call */
			estimate.addRbs(maxAutoAssociationsDelta * INT_SIZE * 27);
		}

		return estimate.get();
	}

	public void cryptoUpdateUsage(final SigUsage sigUsage,
			final BaseTransactionMeta baseMeta,
			final CryptoUpdateMeta cryptoUpdateMeta,
			final UsageAccumulator accumulator) {
		// TODO : will be implemented in a separate PR
	}

	public void cryptoCreateUsage(final SigUsage sigUsage,
			final BaseTransactionMeta baseMeta,
			final CryptoCreateMeta cryptoCreateMeta,
			final UsageAccumulator accumulator) {
		accumulator.resetForTransaction(baseMeta, sigUsage);

		var baseSize = cryptoCreateMeta.getBaseSize();

		var maxAutomaticTokenAssociations = cryptoCreateMeta.getMaxAutomaticAssociations();

		var lifeTime = cryptoCreateMeta.getLifeTime();

		if(maxAutomaticTokenAssociations > 0) {
			baseSize += INT_SIZE;
		}

		/* Variable bytes plus two additional longs for balance and auto-renew period;
		   plus a boolean for receiver sig required. */
		accumulator.addBpt(baseSize + 2 * LONG_SIZE + BOOL_SIZE);
		accumulator.addRbs((CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() + baseSize) * lifeTime);
		/* 	A multiplier '27' is used here to match the cost of each auto-association slot with cost for
			one additional association in a tokenAssociate call */
		accumulator.addRbs(maxAutomaticTokenAssociations * INT_SIZE * lifeTime * 27);
		accumulator.addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}
}
