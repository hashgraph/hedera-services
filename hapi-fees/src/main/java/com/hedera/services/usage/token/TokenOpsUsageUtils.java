package com.hedera.services.usage.token;

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

import com.hedera.services.usage.token.entities.TokenEntitySizes;
import com.hedera.services.usage.token.meta.TokenCreateMeta;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.usage.EstimatorUtils.MAX_ENTITY_LIFETIME;
import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

public enum TokenOpsUsageUtils {
	TOKEN_OPS_USAGE_UTILS;

	public TokenCreateMeta tokenCreateUsageFrom(final TransactionBody txn) {
		int baseSize = getTokenTxnBaseSize(txn);

		TokenCreateTransactionBody op = txn.getTokenCreation();
		var lifetime = op.hasAutoRenewAccount()
				? op.getAutoRenewPeriod().getSeconds()
				: ESTIMATOR_UTILS.relativeLifetime(txn, op.getExpiry().getSeconds());
		lifetime = Math.min(lifetime, MAX_ENTITY_LIFETIME);

		TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();
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

	public int getTokenTxnBaseSize(final TransactionBody txn) {
		TokenCreateTransactionBody op = txn.getTokenCreation();

		TokenEntitySizes tokenEntitySizes = TokenEntitySizes.TOKEN_ENTITY_SIZES;
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

	public static <T> long keySizeIfPresent(T op, Predicate<T> check, Function<T, Key> getter) {
		return check.test(op) ? getAccountKeyStorageSize(getter.apply(op)) : 0L;
	}
}
