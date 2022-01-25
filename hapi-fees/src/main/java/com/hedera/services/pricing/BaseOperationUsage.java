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
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.consensus.ConsensusOpsUsage;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.CryptoTransferMeta;
import com.hedera.services.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.usage.file.FileAppendMeta;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
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
	static final Logger log = LogManager.getLogger(BaseOperationUsage.class);
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
	private static final SignatureMap FOUR_PAIR_SIG_MAP = SignatureMap.newBuilder()
			.addSigPair(SignaturePair.newBuilder()
					.setPubKeyPrefix(ByteString.copyFromUtf8("a"))
					.setEd25519(CANONICAL_SIG))
			.addSigPair(SignaturePair.newBuilder()
					.setPubKeyPrefix(ByteString.copyFromUtf8("b"))
					.setEd25519(CANONICAL_SIG))
			.addSigPair(SignaturePair.newBuilder()
					.setPubKeyPrefix(ByteString.copyFromUtf8("c"))
					.setEd25519(CANONICAL_SIG))
			.addSigPair(SignaturePair.newBuilder()
					.setPubKeyPrefix(ByteString.copyFromUtf8("d"))
					.setEd25519(CANONICAL_SIG))
			.build();
	private static final SigUsage SINGLE_SIG_USAGE = new SigUsage(
			1, ONE_PAIR_SIG_MAP.getSerializedSize(), 1
	);
	private static final SigUsage QUAD_SIG_USAGE = new SigUsage(
			4, FOUR_PAIR_SIG_MAP.getSerializedSize(), 1
	);
	private static final BaseTransactionMeta NO_MEMO_AND_NO_EXPLICIT_XFERS = new BaseTransactionMeta(0, 0);
	private static final Key A_KEY = Key.newBuilder()
			.setEd25519(ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
			.build();
	private static final AccountID AN_ACCOUNT = AccountID.newBuilder().setAccountNum(1_234L).build();

	private static final String A_TOKEN_NAME = "012345678912";
	private static final String A_TOKEN_SYMBOL = "ABCD";
	private static final String BLANK_MEMO = "";
	private static final ByteString BLANK_ALIAS = ByteString.EMPTY;

	private static final TokenOpsUsage TOKEN_OPS_USAGE = new TokenOpsUsage();
	private static final ConsensusOpsUsage CONSENSUS_OPS_USAGE = new ConsensusOpsUsage();
	private static final CryptoOpsUsage CRYPTO_OPS_USAGE = new CryptoOpsUsage();
	private static final FileOpsUsage FILE_OPS_USAGE = new FileOpsUsage();

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
			case FileAppend:
				if (type == DEFAULT) {
					return fileAppend();
				}
				break;
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
					default:
						break;
				}
				break;
			case CryptoCreate:
				if (type == DEFAULT) {
					return cryptoCreate(0);
				}
				break;
			case CryptoUpdate:
				if (type == DEFAULT) {
					return cryptoUpdate(0);
				}
				break;
			case TokenCreate:
				switch (type) {
					case TOKEN_FUNGIBLE_COMMON:
						return fungibleTokenCreate();
					case TOKEN_NON_FUNGIBLE_UNIQUE:
						return uniqueTokenCreate();
					case TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES:
						return fungibleTokenCreateWithCustomFees();
					case TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES:
						return uniqueTokenCreateWithCustomFees();
					default:
						break;
				}
				break;
			case TokenMint:
				if (type == TOKEN_NON_FUNGIBLE_UNIQUE) {
					return uniqueTokenMint();
				} else if (type == TOKEN_FUNGIBLE_COMMON) {
					return fungibleCommonTokenMint();
				}
				break;
			case TokenAccountWipe:
				if (type == TOKEN_NON_FUNGIBLE_UNIQUE) {
					return uniqueTokenWipe();
				} else if (type == TOKEN_FUNGIBLE_COMMON) {
					return fungibleCommonTokenWipe();
				}
				break;
			case TokenBurn:
				if (type == TOKEN_NON_FUNGIBLE_UNIQUE) {
					return uniqueTokenBurn();
				} else if (type == TOKEN_FUNGIBLE_COMMON) {
					return fungibleCommonTokenBurn();
				}
				break;
			case TokenFreezeAccount:
				return tokenFreezeAccount();
			case TokenUnfreezeAccount:
				return tokenUnfreezeAccount();
			case TokenFeeScheduleUpdate:
				return feeScheduleUpdate();
			case TokenPause:
				return tokenPause();
			case TokenUnpause:
				return tokenUnpause();
			case ConsensusSubmitMessage:
				return submitMessage();
			default:
				break;
		}

		throw new IllegalArgumentException("Canonical usage unknown");
	}

	UsageAccumulator cryptoCreate(int autoAssocSlots) {
		final var cryptoCreateTxnBody = CryptoCreateTransactionBody.newBuilder()
				.setMemo(BLANK_MEMO)
				.setMaxAutomaticTokenAssociations(autoAssocSlots)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS))
				.setKey(A_KEY).build();
		final var cryptoCreateMeta = new CryptoCreateMeta(cryptoCreateTxnBody);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoCreateUsage(SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, cryptoCreateMeta, into);
		return into;
	}

	UsageAccumulator cryptoUpdate(int newAutoAssocSlots) {
		final var now = Instant.now().getEpochSecond();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setCryptoUpdateAccount(CryptoUpdateTransactionBody.newBuilder()
						.setMemo(StringValue.of(BLANK_MEMO))
						.setAlias(BLANK_ALIAS)
						.setExpirationTime(Timestamp.newBuilder().setSeconds(now + THREE_MONTHS_IN_SECONDS))
						.setMaxAutomaticTokenAssociations(Int32Value.newBuilder().setValue(newAutoAssocSlots))
						.setAccountIDToUpdate(AN_ACCOUNT)
				).build();
		final var ctx = ExtantCryptoContext.newBuilder()
				.setCurrentExpiry(now)
				.setCurrentMemo(BLANK_MEMO)
				.setCurrentAlias(BLANK_ALIAS)
				.setCurrentKey(A_KEY)
				.setCurrentlyHasProxy(false)
				.setCurrentNumTokenRels(0)
				.setCurrentMaxAutomaticAssociations(0)
				.build();
		final var cryptoUpdateMeta = new CryptoUpdateMeta(canonicalTxn.getCryptoUpdateAccount(), now);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoUpdateUsage(
				SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, cryptoUpdateMeta, ctx, into);
		return into;
	}

	UsageAccumulator fileAppend() {
		/* The canonical usage and context */
		final var opMeta = new FileAppendMeta(1_000, THREE_MONTHS_IN_SECONDS);
		final var into = new UsageAccumulator();
		FILE_OPS_USAGE.fileAppendUsage(SINGLE_SIG_USAGE, opMeta, NO_MEMO_AND_NO_EXPLICIT_XFERS, into);
		return into;
	}


	UsageAccumulator tokenFreezeAccount() {
		final var tokenFreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenFreezeUsageFrom();
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenFreezeUsage(SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenFreezeMeta, into);
		log.info("TokenFreeze base accumulator: {}", into);
		return into;
	}

	UsageAccumulator tokenUnfreezeAccount() {
		final var tokenUnfreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenUnfreezeUsageFrom();
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenFreezeUsage(SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenUnfreezeMeta, into);
		return into;
	}

	UsageAccumulator tokenPause() {
		final var tokenPauseMeta = TOKEN_OPS_USAGE_UTILS.tokenPauseUsageFrom();
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenPauseUsage(SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenPauseMeta, into);
		return into;
	}

	UsageAccumulator tokenUnpause() {
		final var tokenUnpauseMeta = TOKEN_OPS_USAGE_UTILS.tokenUnpauseUsageFrom();
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenUnpauseUsage(SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenUnpauseMeta, into);
		return into;
	}

	UsageAccumulator uniqueTokenBurn() {
		final var target = TokenID.newBuilder().setTokenNum(1_234).build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenBurn(TokenBurnTransactionBody.newBuilder()
						.setToken(target)
						.addAllSerialNumbers(SINGLE_SERIAL_NUM))
				.build();

		final var tokenBurnMeta = TOKEN_OPS_USAGE_UTILS.tokenBurnUsageFrom(canonicalTxn, TOKEN_NON_FUNGIBLE_UNIQUE);
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenBurnUsage(SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenBurnMeta, into);
		return into;
	}

	UsageAccumulator fungibleCommonTokenBurn() {
		final var target = TokenID.newBuilder().setTokenNum(1_235).build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenBurn(TokenBurnTransactionBody.newBuilder()
						.setToken(target)
						.setAmount(1000L)
				)
				.build();

		final var tokenBurnMeta = TOKEN_OPS_USAGE_UTILS.tokenBurnUsageFrom(canonicalTxn, TOKEN_FUNGIBLE_COMMON);
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenBurnUsage(SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenBurnMeta, into);
		return into;
	}

	UsageAccumulator uniqueTokenMint() {
		final var target = TokenID.newBuilder().setTokenNum(1_234).build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenMint(TokenMintTransactionBody.newBuilder()
						.setToken(target)
						.addMetadata(CANONICAL_NFT_METADATA))
				.build();

		final var tokenMintMeta = TOKEN_OPS_USAGE_UTILS.tokenMintUsageFrom(canonicalTxn,
				TOKEN_NON_FUNGIBLE_UNIQUE, THREE_MONTHS_IN_SECONDS);
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenMintUsage(SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenMintMeta, into);
		return into;
	}

	UsageAccumulator fungibleCommonTokenMint() {
		final var target = TokenID.newBuilder().setTokenNum(1_234).build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenMint(TokenMintTransactionBody.newBuilder()
						.setToken(target)
						.setAmount(1000))
				.build();

		final var tokenMintMeta = TOKEN_OPS_USAGE_UTILS.tokenMintUsageFrom(canonicalTxn,
				TOKEN_FUNGIBLE_COMMON, THREE_MONTHS_IN_SECONDS);
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenMintUsage(SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenMintMeta, into);
		return into;
	}

	UsageAccumulator uniqueTokenWipe() {
		final var target = TokenID.newBuilder().setTokenNum(1_234).build();
		final var targetAcct = AccountID.newBuilder().setAccountNum(5_678).build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
						.setToken(target)
						.setAccount(targetAcct)
						.addAllSerialNumbers(SINGLE_SERIAL_NUM))
				.build();

		final var tokenWipeMeta = TOKEN_OPS_USAGE_UTILS.tokenWipeUsageFrom(canonicalTxn);
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenWipeUsage(SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenWipeMeta, into);
		return into;
	}

	UsageAccumulator fungibleCommonTokenWipe() {
		final var target = TokenID.newBuilder().setTokenNum(1_234).build();
		final var targetAcct = AccountID.newBuilder().setAccountNum(5_678).build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
						.setToken(target)
						.setAccount(targetAcct)
						.setAmount(100))
				.build();

		final var tokenWipeMeta = TOKEN_OPS_USAGE_UTILS.tokenWipeUsageFrom(canonicalTxn);
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenWipeUsage(SINGLE_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenWipeMeta, into);
		return into;
	}

	UsageAccumulator fungibleTokenCreateWithCustomFees() {
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setAutoRenewAccount(AN_ACCOUNT)
						.setTreasury(AN_ACCOUNT)
						.setName(A_TOKEN_NAME)
						.setSymbol(A_TOKEN_SYMBOL)
						.setAdminKey(A_KEY)
						.setFeeScheduleKey(A_KEY)
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS))
						.setTokenType(TokenType.FUNGIBLE_COMMON)
						.addCustomFees(CustomFee.newBuilder()
								.setFeeCollectorAccountId(AN_ACCOUNT)
								.setFixedFee(FixedFee.newBuilder()
										.setAmount(100_000_000)
										.build())))
				.build();

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(canonicalTxn);
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenCreateUsage(QUAD_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenCreateMeta, into);
		return into;
	}

	UsageAccumulator fungibleTokenCreate() {
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setTreasury(AN_ACCOUNT)
						.setAutoRenewAccount(AN_ACCOUNT)
						.setName(A_TOKEN_NAME)
						.setSymbol(A_TOKEN_SYMBOL)
						.setAdminKey(A_KEY)
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS))
						.setTokenType(TokenType.FUNGIBLE_COMMON))
				.build();

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(canonicalTxn);
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenCreateUsage(QUAD_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenCreateMeta, into);
		return into;
	}

	UsageAccumulator uniqueTokenCreate() {
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setTreasury(AN_ACCOUNT)
						.setAutoRenewAccount(AN_ACCOUNT)
						.setInitialSupply(0L)
						.setName(A_TOKEN_NAME)
						.setSymbol(A_TOKEN_SYMBOL)
						.setAdminKey(A_KEY)
						.setSupplyKey(A_KEY)
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS))
						.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE))
				.build();

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(canonicalTxn);
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenCreateUsage(QUAD_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenCreateMeta, into);
		return into;
	}

	UsageAccumulator uniqueTokenCreateWithCustomFees() {
		final var canonicalTxn = TransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.newBuilder()
						.setAutoRenewAccount(AN_ACCOUNT)
						.setTreasury(AN_ACCOUNT)
						.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
						.setInitialSupply(0L)
						.setName(A_TOKEN_NAME)
						.setSymbol(A_TOKEN_SYMBOL)
						.setAdminKey(A_KEY)
						.setSupplyKey(A_KEY)
						.setFeeScheduleKey(A_KEY)
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS))
						.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
						.addCustomFees(CustomFee.newBuilder()
								.setFeeCollectorAccountId(AN_ACCOUNT)
								.setFixedFee(FixedFee.newBuilder()
										.setAmount(100_000_000)
										.build())))
				.build();

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(canonicalTxn);
		final var into = new UsageAccumulator();
		TOKEN_OPS_USAGE.tokenCreateUsage(QUAD_SIG_USAGE, NO_MEMO_AND_NO_EXPLICIT_XFERS, tokenCreateMeta, into);
		return into;

	}

	UsageAccumulator submitMessage() {
		final var opMeta = new SubmitMessageMeta(100);
		final var into = new UsageAccumulator();
		CONSENSUS_OPS_USAGE.submitMessageUsage(SINGLE_SIG_USAGE, opMeta, NO_MEMO_AND_NO_EXPLICIT_XFERS, into);
		return into;
	}

	UsageAccumulator feeScheduleUpdate() {
		/* A canonical op */
		final var target = TokenID.newBuilder().setShardNum(1).setRealmNum(2).setTokenNum(3).build();
		final List<CustomFee> theNewSchedule = List.of(
				CustomFee.newBuilder().setFixedFee(FixedFee.newBuilder()
								.setAmount(123L)
								.setDenominatingTokenId(target))
						.build());

		/* The canonical usage and context */
		final var newReprBytes = TOKEN_OPS_USAGE.bytesNeededToRepr(theNewSchedule);
		final var opMeta = new FeeScheduleUpdateMeta(0L, newReprBytes);
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

	UsageAccumulator hbarCryptoTransfer() {
		final var txnUsageMeta = new BaseTransactionMeta(0, 2);
		final var xferUsageMeta = new CryptoTransferMeta(380, 0,
				0, 0);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoTransferUsage(SINGLE_SIG_USAGE, xferUsageMeta, txnUsageMeta, into);

		return into;
	}

	UsageAccumulator htsCryptoTransfer() {
		final var xferUsageMeta = new CryptoTransferMeta(380, 1,
				2, 0);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoTransferUsage(SINGLE_SIG_USAGE, xferUsageMeta, NO_MEMO_AND_NO_EXPLICIT_XFERS, into);

		return into;
	}

	UsageAccumulator htsCryptoTransferWithCustomFee() {
		final var xferUsageMeta = new CryptoTransferMeta(380, 1,
				2, 0);
		xferUsageMeta.setCustomFeeHbarTransfers(2);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoTransferUsage(SINGLE_SIG_USAGE, xferUsageMeta, NO_MEMO_AND_NO_EXPLICIT_XFERS, into);

		return into;
	}

	UsageAccumulator nftCryptoTransfer() {
		final var xferUsageMeta = new CryptoTransferMeta(380, 1,
				0, 1);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoTransferUsage(SINGLE_SIG_USAGE, xferUsageMeta, NO_MEMO_AND_NO_EXPLICIT_XFERS, into);

		return into;
	}

	UsageAccumulator nftCryptoTransferWithCustomFee() {
		final var xferUsageMeta = new CryptoTransferMeta(380, 1,
				0, 1);
		xferUsageMeta.setCustomFeeHbarTransfers(2);
		final var into = new UsageAccumulator();
		CRYPTO_OPS_USAGE.cryptoTransferUsage(SINGLE_SIG_USAGE, xferUsageMeta, NO_MEMO_AND_NO_EXPLICIT_XFERS, into);

		return into;
	}
}
