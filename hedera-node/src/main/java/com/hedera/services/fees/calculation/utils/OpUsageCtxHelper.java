package com.hedera.services.fees.calculation.utils;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.usage.file.FileAppendMeta;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.usage.token.meta.TokenBurnMeta;
import com.hedera.services.usage.token.meta.TokenMintMeta;
import com.hedera.services.usage.token.meta.TokenWipeMeta;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.fcmap.FCMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FIXED_FEE;
import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FRACTIONAL_FEE;
import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

@Singleton
public class OpUsageCtxHelper {
	private static final ExtantFeeScheduleContext MISSING_FEE_SCHEDULE_UPDATE_CTX =
			new ExtantFeeScheduleContext(0, 0);

	private final TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();

	private final StateView workingView;
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;

	@Inject
	public OpUsageCtxHelper(
			StateView workingView,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens
	) {
		this.tokens = tokens;
		this.workingView = workingView;
	}

	public FileAppendMeta metaForFileAppend(TransactionBody txn) {
		final var op = txn.getFileAppend();
		final var fileMeta = workingView.attrOf(op.getFileID());

		final var effCreationTime = txn.getTransactionID().getTransactionValidStart().getSeconds();
		final var effExpiration = fileMeta.map(HFileMeta::getExpiry).orElse(effCreationTime);
		final var effLifetime = effExpiration - effCreationTime;

		return new FileAppendMeta(op.getContents().size(), effLifetime);
	}

	public ExtantFeeScheduleContext ctxForFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody op) {
		final var key = MerkleEntityId.fromTokenId(op.getTokenId());
		final var token = tokens.get().get(key);
		if (token == null) {
			return MISSING_FEE_SCHEDULE_UPDATE_CTX;
		}
		return new ExtantFeeScheduleContext(token.expiry(), curFeeScheduleReprSize(token.customFeeSchedule()));
	}

	private SubType getSubTypeFor(TokenID token) {
		final var tokenType = workingView.tokenType(token);
		SubType subType = null;
		if(tokenType.isPresent()) {
			if(tokenType.get() == NON_FUNGIBLE_UNIQUE) {
				subType = TOKEN_NON_FUNGIBLE_UNIQUE;
			} else {
				subType = TOKEN_FUNGIBLE_COMMON;
			}
		}
		return subType;
	}

	public TokenBurnMeta metaForTokenBurn(TxnAccessor txn) {
		final var token = txn.getTxn().getTokenBurn().getToken();
		final var subType = getSubTypeFor(token);
		return TOKEN_OPS_USAGE_UTILS.tokenBurnUsageFrom(txn.getTxn(), subType);
	}

	public TokenWipeMeta metaForTokenWipe(TxnAccessor txn) {
		final var token = txn.getTxn().getTokenWipe().getToken();
		final var subType = getSubTypeFor(token);
		return TOKEN_OPS_USAGE_UTILS.tokenWipeUsageFrom(txn.getTxn(), subType);
	}

	public TokenMintMeta metaForTokenMint(TxnAccessor txn) {
		final var token = txn.getTxn().getTokenMint().getToken();
		final var subType = getSubTypeFor(token);

		long lifeTime = 0L;
		if(subType == TOKEN_NON_FUNGIBLE_UNIQUE) {
			final var now = txn.getTxnId().getTransactionValidStart().getSeconds();
			final var tokenIfPresent = workingView.tokenWith(token);
			lifeTime = tokenIfPresent.map(t -> Math.max(0L, t.expiry() - now)).orElse(0L);
		}
		return TOKEN_OPS_USAGE_UTILS.tokenMintUsageFrom(txn.getTxn(), subType, lifeTime);
	}

	private int curFeeScheduleReprSize(List<FcCustomFee> feeSchedule) {
		int numFixedHbarFees = 0;
		int numFixedHtsFees = 0;
		int numFractionalFees = 0;
		int numRoyaltyNoFallbackFees = 0;
		int numRoyaltyHtsFallbackFees = 0;
		int numRoyaltyHbarFallbackFees = 0;
		for (var fee : feeSchedule) {
			if (fee.getFeeType() == FIXED_FEE) {
				if (fee.getFixedFeeSpec().getTokenDenomination() != null) {
					numFixedHtsFees++;
				} else {
					numFixedHbarFees++;
				}
			} else if (fee.getFeeType() == FRACTIONAL_FEE) {
				numFractionalFees++;
			} else {
				final var royaltyFee = fee.getRoyaltyFeeSpec();
				final var fallbackFee = royaltyFee.getFallbackFee();
				if (fallbackFee != null) {
					if (fallbackFee.getTokenDenomination() != null) {
						numRoyaltyHtsFallbackFees++;
					} else {
						numRoyaltyHbarFallbackFees++;
					}
				} else {
					numRoyaltyNoFallbackFees++;
				}
			}
		}
		return tokenOpsUsage.bytesNeededToRepr(
				numFixedHbarFees,
				numFixedHtsFees,
				numFractionalFees,
				numRoyaltyNoFallbackFees,
				numRoyaltyHtsFallbackFees,
				numRoyaltyHbarFallbackFees);
	}
}
