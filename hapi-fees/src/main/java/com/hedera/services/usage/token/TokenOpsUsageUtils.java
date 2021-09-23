package com.hedera.services.usage.token;

/*-
 * ‌
 * Hedera Services API Fees
 *
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 *
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

import com.google.protobuf.ByteString;
import com.hedera.services.usage.token.entities.TokenEntitySizes;
import com.hedera.services.usage.token.meta.TokenAssociateMeta;
import com.hedera.services.usage.token.meta.TokenBurnMeta;
import com.hedera.services.usage.token.meta.TokenCreateMeta;
import com.hedera.services.usage.token.meta.TokenDeleteMeta;
import com.hedera.services.usage.token.meta.TokenDissociateMeta;
import com.hedera.services.usage.token.meta.TokenGrantKycMeta;
import com.hedera.services.usage.token.meta.TokenFreezeMeta;
import com.hedera.services.usage.token.meta.TokenMintMeta;
import com.hedera.services.usage.token.meta.TokenRevokeKycMeta;
import com.hedera.services.usage.token.meta.TokenUpdateMeta;
import com.hedera.services.usage.token.meta.TokenUnfreezeMeta;
import com.hedera.services.usage.token.meta.TokenWipeMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.time.Instant;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

import static com.hedera.services.usage.EstimatorUtils.MAX_ENTITY_LIFETIME;
import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;
import static com.hedera.services.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;

public enum TokenOpsUsageUtils {
	TOKEN_OPS_USAGE_UTILS;

	public TokenCreateMeta tokenCreateUsageFrom(final TransactionBody txn) {
		final var baseSize = getTokenTxnBaseSize(txn);

		final var op = txn.getTokenCreation();
		var lifetime = op.hasAutoRenewAccount()
				? op.getAutoRenewPeriod().getSeconds()
				: ESTIMATOR_UTILS.relativeLifetime(txn, op.getExpiry().getSeconds());
		lifetime = Math.min(lifetime, MAX_ENTITY_LIFETIME);

		final var tokenOpsUsage = new TokenOpsUsage();
		final var feeSchedulesSize = op.getCustomFeesCount() > 0
				? tokenOpsUsage.bytesNeededToRepr(op.getCustomFeesList()) : 0;

		SubType chosenType;
		final var usesCustomFees = op.hasFeeScheduleKey() || op.getCustomFeesCount() > 0;
		if (op.getTokenType() == NON_FUNGIBLE_UNIQUE) {
			chosenType = usesCustomFees ? TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES : TOKEN_NON_FUNGIBLE_UNIQUE;
		} else {
			chosenType = usesCustomFees ? TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES : TOKEN_FUNGIBLE_COMMON;
		}

		return new TokenCreateMeta.Builder()
				.baseSize(baseSize)
				.lifeTime(lifetime)
				.customFeeScheleSize(feeSchedulesSize)
				.fungibleNumTransfers(op.getInitialSupply() > 0 ? 1 : 0)
				.nftsTranfers(0)
				.numTokens(1)
				.networkRecordRb(BASIC_ENTITY_ID_SIZE)
				.subType(chosenType)
				.build();
	}

	public TokenUpdateMeta tokenUpdateUsageFrom(final TransactionBody txn) {
		final var op = txn.getTokenUpdate();
		final var keysSize = getTokenUpdateKeysSize(txn);

		final boolean removesAutoRenewAccount = removesAutoRenewAccount(txn.getTokenUpdate());
		final boolean hasAutoRenewAccount = op.hasAutoRenewAccount();
		long autoRenewPeriod = 0;
		if(hasAutoRenewAccount) {
			autoRenewPeriod = op.getAutoRenewPeriod().getSeconds();
		}
		final boolean hasExpiry = op.hasExpiry();
		long newExpiry = 0;
		if(hasExpiry) {
			newExpiry = op.getExpiry().getSeconds();
		}

		long effectiveTxnStart = txn.getTransactionID().getTransactionValidStart().getSeconds();
		effectiveTxnStart = effectiveTxnStart > 0 ? effectiveTxnStart : Instant.now().getEpochSecond();

		return TokenUpdateMeta.newBuilder()
				.setNewKeysLen(keysSize)
				.setHasTreasure(op.hasTreasury())
				.setHasAutoRenewAccount(op.hasAutoRenewAccount())
				.setRemoveAutoRenewAccount(removesAutoRenewAccount)
				.setNewAutoRenewPeriod(autoRenewPeriod)
				.setNewExpiry(newExpiry)
				.setNewMemoLen(op.hasMemo() ? op.getMemo().getValue().length() : 0)
				.setNewNameLen(op.getName().length())
				.setNewSymLen(op.getSymbol().length())
				.setNewEffectiveTxnStartTime(effectiveTxnStart)
				.build();
	}

	public TokenMintMeta tokenMintUsageFrom(
			final TransactionBody txn,
			final SubType subType,
			final long expectedLifeTime
	) {
		var op = txn.getTokenMint();
		int bpt = 0;
		long rbs = 0;
		int transferRecordRb = 0;
		if (subType == TOKEN_NON_FUNGIBLE_UNIQUE) {
			var metadataBytes = 0;
			for (ByteString o : op.getMetadataList()) {
				metadataBytes += o.size();
			}
			bpt = metadataBytes;
			rbs = metadataBytes * expectedLifeTime;
			transferRecordRb = TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 0, op.getMetadataCount());
		} else {
			bpt = LONG_SIZE;
			transferRecordRb = TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 1, 0);
		}
		bpt += BASIC_ENTITY_ID_SIZE;
		return new TokenMintMeta(bpt, subType, transferRecordRb, rbs);
	}


	public TokenDeleteMeta tokenDeleteUsageFrom() {
		return new TokenDeleteMeta(BASIC_ENTITY_ID_SIZE);
	}


	public TokenFreezeMeta tokenFreezeUsageFrom() {
		return new TokenFreezeMeta(2 * BASIC_ENTITY_ID_SIZE);
	}

	public TokenUnfreezeMeta tokenUnfreezeUsageFrom() {
		return new TokenUnfreezeMeta(2 * BASIC_ENTITY_ID_SIZE);
	}

	public TokenBurnMeta tokenBurnUsageFrom(final TransactionBody txn) {
		var op = txn.getTokenBurn();
		final var subType = op.getSerialNumbersCount() > 0 ? TOKEN_NON_FUNGIBLE_UNIQUE : TOKEN_FUNGIBLE_COMMON;
		return tokenBurnUsageFrom(txn, subType);
	}

	public TokenBurnMeta tokenBurnUsageFrom(final TransactionBody txn, final SubType subType) {
		var op = txn.getTokenBurn();
		return retrieveRawDataFrom(subType, op::getSerialNumbersCount, TokenBurnMeta::new);
	}

	public TokenWipeMeta tokenWipeUsageFrom(final TransactionBody txn) {
		var op = txn.getTokenWipe();
		final var subType = op.getSerialNumbersCount() > 0 ? TOKEN_NON_FUNGIBLE_UNIQUE : TOKEN_FUNGIBLE_COMMON;
		return tokenWipeUsageFrom(txn, subType);
	}

	public TokenWipeMeta tokenWipeUsageFrom(final TransactionBody txn, final SubType subType) {
		var op = txn.getTokenWipe();

		return retrieveRawDataFrom(subType, op::getSerialNumbersCount, TokenWipeMeta::new);
	}

	public TokenGrantKycMeta tokenGrantKycUsageFrom() {
		return new TokenGrantKycMeta(2 * BASIC_ENTITY_ID_SIZE);
	}

	public TokenRevokeKycMeta tokenRevokeKycUsageFrom() {
		return new TokenRevokeKycMeta(2 * BASIC_ENTITY_ID_SIZE);
	}

	public TokenAssociateMeta tokenAssociateUsageFrom(final TransactionBody txn) {
		final var op = txn.getTokenAssociate();
		int numOfTokens = op.getTokensCount();
		return new TokenAssociateMeta(BASIC_ENTITY_ID_SIZE * (numOfTokens + 1), numOfTokens);
	}

	public TokenDissociateMeta tokenDissociateUsageFrom(TransactionBody txn) {
		final var op = txn.getTokenDissociate();
		int numOfTokens = op.getTokensCount();
		return new TokenDissociateMeta(BASIC_ENTITY_ID_SIZE * (numOfTokens + 1));
	}

	public <R> R retrieveRawDataFrom(SubType subType, IntSupplier getDataForNFT, Producer<R> producer) {
		int serialNumsCount = 0;
		int bpt = 0;
		int transferRecordRb = 0;
		if (subType == TOKEN_NON_FUNGIBLE_UNIQUE) {
			serialNumsCount = getDataForNFT.getAsInt();
			transferRecordRb = TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 0, serialNumsCount);
			bpt = serialNumsCount * LONG_SIZE;
		} else {
			bpt = LONG_SIZE;
			transferRecordRb = TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 1, 0);
		}
		bpt += BASIC_ENTITY_ID_SIZE;

		return producer.create(bpt, subType, transferRecordRb, serialNumsCount);
	}

	@FunctionalInterface
	interface Producer<R> {
		R create(int bpt, SubType subType, long recordDb, int t);
	}

	public int getTokenTxnBaseSize(final TransactionBody txn) {
		final var op = txn.getTokenCreation();

		final var tokenEntitySizes = TokenEntitySizes.TOKEN_ENTITY_SIZES;
		var baseSize = tokenEntitySizes.totalBytesInTokenReprGiven(op.getSymbol(), op.getName());
		baseSize += keySizeIfPresent(
				op, TokenCreateTransactionBody::hasKycKey, TokenCreateTransactionBody::getKycKey);
		baseSize += keySizeIfPresent(
				op, TokenCreateTransactionBody::hasWipeKey, TokenCreateTransactionBody::getWipeKey);
		baseSize += keySizeIfPresent(
				op, TokenCreateTransactionBody::hasAdminKey, TokenCreateTransactionBody::getAdminKey);
		baseSize += keySizeIfPresent(
				op, TokenCreateTransactionBody::hasSupplyKey, TokenCreateTransactionBody::getSupplyKey);
		baseSize += keySizeIfPresent(
				op, TokenCreateTransactionBody::hasFreezeKey, TokenCreateTransactionBody::getFreezeKey);
		baseSize += keySizeIfPresent(
				op, TokenCreateTransactionBody::hasFeeScheduleKey, TokenCreateTransactionBody::getFeeScheduleKey);
		baseSize += op.getMemoBytes().size();
		if (op.hasAutoRenewAccount()) {
			baseSize += BASIC_ENTITY_ID_SIZE;
		}
		return baseSize;
	}

	public int getTokenUpdateKeysSize(final TransactionBody txn) {
		final var op = txn.getTokenUpdate();

		int keysSize = 0;
		keysSize += keySizeIfPresent(
				op, TokenUpdateTransactionBody::hasKycKey, TokenUpdateTransactionBody::getKycKey);
		keysSize += keySizeIfPresent(
				op, TokenUpdateTransactionBody::hasWipeKey, TokenUpdateTransactionBody::getWipeKey);
		keysSize += keySizeIfPresent(
				op, TokenUpdateTransactionBody::hasAdminKey, TokenUpdateTransactionBody::getAdminKey);
		keysSize += keySizeIfPresent(
				op, TokenUpdateTransactionBody::hasSupplyKey, TokenUpdateTransactionBody::getSupplyKey);
		keysSize += keySizeIfPresent(
				op, TokenUpdateTransactionBody::hasFreezeKey, TokenUpdateTransactionBody::getFreezeKey);
		keysSize += keySizeIfPresent(
				op, TokenUpdateTransactionBody::hasFeeScheduleKey, TokenUpdateTransactionBody::getFeeScheduleKey);
		return keysSize;
	}

	public static <T> long keySizeIfPresent(final T op, final Predicate<T> check, final Function<T, Key> getter) {
		return check.test(op) ? getAccountKeyStorageSize(getter.apply(op)) : 0L;
	}

	private boolean removesAutoRenewAccount(TokenUpdateTransactionBody op) {
		return op.hasAutoRenewAccount() && op.getAutoRenewAccount().equals(AccountID.getDefaultInstance());
	}
}
