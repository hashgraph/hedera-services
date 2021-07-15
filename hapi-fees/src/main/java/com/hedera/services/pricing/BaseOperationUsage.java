package com.hedera.services.pricing;

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

import com.google.protobuf.ByteString;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.consensus.ConsensusOpsUsage;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.CryptoTransferMeta;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.TokenBurnUsage;
import com.hedera.services.usage.token.TokenMintUsage;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.usage.token.TokenWipeUsage;
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.List;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;

/**
 * Provides the resource usage of the "base configuration" for each Hedera operation.
 *
 * The base configuration of an operation is usually the cheapest version of the
 * operation that still does something useful. (For example, the base CryptoTransfer
 * adjusts only two ℏ accounts using one signature, the base TokenFeeScheduleUpdate
 * adds a single custom HTS fee to a token, etc.)
 */
class BaseOperationUsage {
	private static final long THREE_MONTHS_IN_SECONDS = 7776000L;
	private static final ByteString CANONICAL_SIG = ByteString.copyFromUtf8(
			"0123456789012345678901234567890123456789012345678901234567890123");
	private static final List<Long> SINGLE_SERIAL_NUM = List.of(1L);
	private static final ByteString CANONICAL_NFT_METADATA = ByteString.copyFromUtf8(
			"0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");
	private static final SignatureMap ONE_PAIR_SIG_MAP = SignatureMap.newBuilder()
			.addSigPair(SignaturePair.newBuilder()
					.setPubKeyPrefix(ByteString.copyFromUtf8("a"))
					.setEd25519(CANONICAL_SIG))
			.build();
	private static final SigUsage SINGLE_SIG_USAGE = new SigUsage(
			1, ONE_PAIR_SIG_MAP.getSerializedSize(), 1
	);
	private static final BaseTransactionMeta NO_MEMO_AND_NO_EXPLICIT_XFERS = new BaseTransactionMeta(0, 0);

	private static final TokenOpsUsage TOKEN_OPS_USAGE = new TokenOpsUsage();
	private static final ConsensusOpsUsage CONSENSUS_OPS_USAGE = new ConsensusOpsUsage();
	private static final CryptoOpsUsage CRYPTO_OPS_USAGE = new CryptoOpsUsage();

	/**
	 * Returns the total resource usage in the new {@link UsageAccumulator} process
	 * object for the base configuration of the given type of the given operation.
	 *
	 * @param function
	 * 		the operation of interest
	 * @param type
	 * 		the type of interest
	 * @return the total resource usage of the base configuration
	 */
	UsageAccumulator baseUsageFor(HederaFunctionality function, SubType type) {

		switch (function) {
			case CryptoTransfer:
				switch (type) {
					case DEFAULT:
						return hbarCryptoTransfer();
					case TOKEN_FUNGIBLE_COMMON:
						return htsCryptoTransfer();
					case TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES:
						return htsCryptoTransferWithCustomFee();
					case TOKEN_NON_FUNGIBLE_UNIQUE:
						return nftCryptoTransfer();
					case TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES:
						return nftCryptoTransferWithCustomFee();
				}
			case TokenMint:
				if (type == TOKEN_NON_FUNGIBLE_UNIQUE) {
					return uniqueTokenMint();
				}
			case TokenAccountWipe:
				if (type == TOKEN_NON_FUNGIBLE_UNIQUE) {
					return uniqueTokenWipe();
				}
			case TokenBurn:
				if (type == TOKEN_NON_FUNGIBLE_UNIQUE) {
					return uniqueTokenBurn();
				}
			case ConsensusSubmitMessage:
				return submitMessage();
			case TokenFeeScheduleUpdate:
				return feeScheduleUpdate();
			default:
				break;
		}

		throw new IllegalArgumentException("Canonical usage unknown");
	}

	private UsageAccumulator uniqueTokenBurn() {
		final var target = TokenID.newBuilder().setTokenNum(1_234).build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenBurn(TokenBurnTransactionBody.newBuilder()
						.setToken(target)
						.addAllSerialNumbers(SINGLE_SERIAL_NUM))
				.build();
		final var helper = new TxnUsageEstimator(SINGLE_SIG_USAGE, canonicalTxn, ESTIMATOR_UTILS);
		final var estimator = new TokenBurnUsage(canonicalTxn, helper);
		final var baseUsage = estimator
				.givenSubType(TOKEN_NON_FUNGIBLE_UNIQUE)
				.get();
		return UsageAccumulator.fromGrpc(baseUsage);
	}

	private UsageAccumulator uniqueTokenMint() {
		final var target = TokenID.newBuilder().setTokenNum(1_234).build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenMint(TokenMintTransactionBody.newBuilder()
						.setToken(target)
						.addMetadata(CANONICAL_NFT_METADATA))
				.build();

		final var helper = new TxnUsageEstimator(SINGLE_SIG_USAGE, canonicalTxn, ESTIMATOR_UTILS);
		final var estimator = new TokenMintUsage(canonicalTxn, helper);
		final var baseUsage = estimator
				.givenSubType(TOKEN_NON_FUNGIBLE_UNIQUE)
				.givenExpectedLifetime(THREE_MONTHS_IN_SECONDS)
				.get();
		return UsageAccumulator.fromGrpc(baseUsage);
	}

	private UsageAccumulator uniqueTokenWipe() {
		final var target = TokenID.newBuilder().setTokenNum(1_234).build();
		final var targetAcct = AccountID.newBuilder().setAccountNum(5_678).build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
						.setToken(target)
						.setAccount(targetAcct)
						.addAllSerialNumbers(SINGLE_SERIAL_NUM))
				.build();

		final var helper = new TxnUsageEstimator(SINGLE_SIG_USAGE, canonicalTxn, ESTIMATOR_UTILS);
		final var estimator = new TokenWipeUsage(canonicalTxn, helper);
		final var baseUsage = estimator
				.givenSubType(TOKEN_NON_FUNGIBLE_UNIQUE)
				.get();

		return UsageAccumulator.fromGrpc(baseUsage);
	}

	private UsageAccumulator submitMessage() {
		final var opMeta = new SubmitMessageMeta(100);
		final var into = new UsageAccumulator();
		CONSENSUS_OPS_USAGE.submitMessageUsage(SINGLE_SIG_USAGE, opMeta, NO_MEMO_AND_NO_EXPLICIT_XFERS, into);
		return into;
	}

	private UsageAccumulator feeScheduleUpdate() {
		/* A canonical op */
		final var target = TokenID.newBuilder().setShardNum(1).setRealmNum(2).setTokenNum(3).build();
		final List<CustomFee> theNewSchedule = List.of(
				CustomFee.newBuilder().setFixedFee(FixedFee.newBuilder()
						.setAmount(123L)
						.setDenominatingTokenId(target))
						.build());
		final var op = TokenFeeScheduleUpdateTransactionBody.newBuilder()
				.setTokenId(target)
				.addAllCustomFees(theNewSchedule)
				.build();

		/* The canonical usage and context */
		final var newReprBytes = TOKEN_OPS_USAGE.bytesNeededToRepr(theNewSchedule);
		final var grpcReprBytes = op.getSerializedSize() - op.getTokenId().getSerializedSize();
		final var opMeta = new FeeScheduleUpdateMeta(0L, newReprBytes, grpcReprBytes);
		final var feeScheduleCtx = new ExtantFeeScheduleContext(THREE_MONTHS_IN_SECONDS, 0);

		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.feeScheduleUpdateUsage(
				SINGLE_SIG_USAGE,
				NO_MEMO_AND_NO_EXPLICIT_XFERS,
				opMeta,
				feeScheduleCtx,
				into);
		return into;
	}

	private UsageAccumulator hbarCryptoTransfer() {
		final var txnUsageMeta = new BaseTransactionMeta(0, 2);
		final var xferUsageMeta = new CryptoTransferMeta(380, 0,
				0, 0);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoTransferUsage(SINGLE_SIG_USAGE, xferUsageMeta, txnUsageMeta, into);

		return into;
	}

	private UsageAccumulator htsCryptoTransfer() {
		final var xferUsageMeta = new CryptoTransferMeta(380, 1,
				2, 0);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoTransferUsage(SINGLE_SIG_USAGE, xferUsageMeta, NO_MEMO_AND_NO_EXPLICIT_XFERS, into);

		return into;
	}

	private UsageAccumulator htsCryptoTransferWithCustomFee() {
		final var xferUsageMeta = new CryptoTransferMeta(380, 1,
				2, 0);
		xferUsageMeta.setCustomFeeHbarTransfers(1);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoTransferUsage(SINGLE_SIG_USAGE, xferUsageMeta, NO_MEMO_AND_NO_EXPLICIT_XFERS, into);

		return into;
	}

	private UsageAccumulator nftCryptoTransfer() {
		final var xferUsageMeta = new CryptoTransferMeta(380, 1,
				0, 1);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoTransferUsage(SINGLE_SIG_USAGE, xferUsageMeta, NO_MEMO_AND_NO_EXPLICIT_XFERS, into);

		return into;
	}

	private UsageAccumulator nftCryptoTransferWithCustomFee() {
		final var xferUsageMeta = new CryptoTransferMeta(380, 1,
				0, 1);
		xferUsageMeta.setCustomFeeTokenTransfers(1);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoTransferUsage(SINGLE_SIG_USAGE, xferUsageMeta, NO_MEMO_AND_NO_EXPLICIT_XFERS, into);

		return into;
	}
}
