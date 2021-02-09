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

import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.QueryUsage;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.TxnUsage.keySizeIfPresent;
import static com.hedera.services.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASE_FILEINFO_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

public class CryptoOpsUsage {
	static EstimatorFactory txnEstimateFactory = TxnUsageEstimator::new;
	static Function<ResponseType, QueryUsage> queryEstimateFactory = QueryUsage::new;

	public FeeData cryptoInfoUsage(Query cryptoInfoReq, ExtantCryptoContext ctx) {
		var op = cryptoInfoReq.getCryptoGetInfo();

		var estimate = queryEstimateFactory.apply(op.getHeader().getResponseType());
		estimate.updateTb(BASIC_ENTITY_ID_SIZE);
		long extraRb = 0;
		extraRb += ctx.currentMemo().getBytes(StandardCharsets.UTF_8).length;
		extraRb += getAccountKeyStorageSize(ctx.currentKey());
		if (ctx.currentlyHasProxy()) {
			extraRb += BASIC_ENTITY_ID_SIZE;
		}
		estimate.updateRb(CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() + extraRb);

		return estimate.get();
	}

	public FeeData cryptoUpdateUsage(TransactionBody cryptoUpdate, SigUsage sigUsage, ExtantCryptoContext ctx) {
		var op = cryptoUpdate.getCryptoUpdateAccount();

		long keyBytesUsed = op.hasKey() ? getAccountKeyStorageSize(op.getKey()) : 0;
		long msgBytesUsed = BASIC_ENTITY_ID_SIZE
				+ op.getMemo().getValueBytes().size()
				+ keyBytesUsed
				+ (op.hasExpirationTime() ? LONG_SIZE : 0)
				+ (op.hasAutoRenewPeriod() ? LONG_SIZE : 0)
				+ (op.hasProxyAccountID() ? BASIC_ENTITY_ID_SIZE : 0);
		var estimate = txnEstimateFactory.get(sigUsage, cryptoUpdate, ESTIMATOR_UTILS);
		estimate.addBpt(msgBytesUsed);

		long newVariableBytes = 0;
		newVariableBytes += !op.hasMemo()
				? ctx.currentMemo().getBytes(StandardCharsets.UTF_8).length
				: op.getMemo().getValueBytes().size();
		newVariableBytes += !op.hasKey() ? getAccountKeyStorageSize(ctx.currentKey()) : keyBytesUsed;
		newVariableBytes += (op.hasProxyAccountID() || ctx.currentlyHasProxy()) ? BASIC_ENTITY_ID_SIZE : 0;

		long oldVariableBytes = ctx.currentNonBaseRb();
		long newLifetime = ESTIMATOR_UTILS.relativeLifetime(cryptoUpdate, op.getExpirationTime().getSeconds());
		long oldLifetime = ESTIMATOR_UTILS.relativeLifetime(cryptoUpdate, ctx.currentExpiry());
		long rbsDelta = ESTIMATOR_UTILS.changeInBsUsage(oldVariableBytes, oldLifetime, newVariableBytes, newLifetime);
		if (rbsDelta > 0) {
			estimate.addRbs(rbsDelta);
		}

		return estimate.get();
	}

	public FeeData cryptoCreateUsage(TransactionBody cryptoCreation, SigUsage sigUsage) {
		var op = cryptoCreation.getCryptoCreateAccount();

		int variableBytes = 0;
		variableBytes += op.getMemoBytes().size();
		variableBytes += keySizeIfPresent(op, CryptoCreateTransactionBody::hasKey, CryptoCreateTransactionBody::getKey);
		if (op.hasProxyAccountID()) {
			variableBytes += BASIC_ENTITY_ID_SIZE;
		}

		var lifetime = op.getAutoRenewPeriod().getSeconds();

		var estimate = txnEstimateFactory.get(sigUsage, cryptoCreation, ESTIMATOR_UTILS);
		/* Variable bytes plus two additional longs for balance and auto-renew period;
		   plus a boolean for receiver sig required. */
		estimate.addBpt(variableBytes + 2 * LONG_SIZE + BOOL_SIZE);
		estimate.addRbs((CRYPTO_ENTITY_SIZES.fixedBytesInAccountRepr() + variableBytes) * lifetime);
		estimate.addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());

		return estimate.get();
	}
}
