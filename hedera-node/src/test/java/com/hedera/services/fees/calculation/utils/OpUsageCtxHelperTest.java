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

import com.google.protobuf.ByteString;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.config.MockFileNumbers;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.ledger.accounts.AutoAccountsManager;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.FixedFeeSpec;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.state.merkle.MerkleAccountState.DEFAULT_MEMO;
import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.services.state.submerkle.FcCustomFee.fractionalFee;
import static com.hedera.services.state.submerkle.FcCustomFee.royaltyFee;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class OpUsageCtxHelperTest {
	private final FileNumbers fileNumbers = new MockFileNumbers();

	@Mock
	private MerkleMap<EntityNum, MerkleToken> tokens;
	@Mock
	private StateView workingView;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private SignedTxnAccessor accessor;

	private OpUsageCtxHelper subject;
	@Mock
	private AutoAccountsManager autoAccounts;

	@BeforeEach
	void setUp() {
		subject = new OpUsageCtxHelper(workingView, fileNumbers, () -> tokens, autoAccounts);
	}

	@Test
	void returnsKnownExpiry() {
		given(workingView.attrOf(targetFile)).willReturn(Optional.of(fileMeta));

		// when:
		final var opMeta = subject.metaForFileAppend(stdAppendTxn);

		// then:
		assertEquals(newFileBytes, opMeta.getBytesAdded());
		assertEquals(then - now, opMeta.getLifetime());
	}

	@Test
	void shortCircuitsFileAppendMetaForSpecialFile() {
		final var opMeta = subject.metaForFileAppend(specialAppendTxn);

		assertEquals(newFileBytes, opMeta.getBytesAdded());
		assertEquals(7776000L, opMeta.getLifetime());
	}

	@Test
	void returnsZeroLifetimeForUnknownExpiry() {
		// when:
		final var opMeta = subject.metaForFileAppend(stdAppendTxn);

		// then:
		assertEquals(newFileBytes, opMeta.getBytesAdded());
		assertEquals(0, opMeta.getLifetime());
	}

	@Test
	void returnsZerosForMissingToken() {
		// setup:
		extant.setFeeSchedule(fcFees());

		// when:
		final var ctx = subject.ctxForFeeScheduleUpdate(op());

		// then:
		assertEquals(0L, ctx.expiry());
		assertEquals(0, ctx.numBytesInFeeScheduleRepr());
	}

	@Test
	void returnsExpectedCtxForExtantToken() {
		// setup:
		extant.setFeeSchedule(fcFees());
		final var expBytes = tokenOpsUsage.bytesNeededToRepr(1, 2, 3, 1, 1, 1);

		given(tokens.get(EntityNum.fromTokenId(target))).willReturn(extant);

		// when:
		final var ctx = subject.ctxForFeeScheduleUpdate(op());

		// then:
		assertEquals(now, ctx.expiry());
		assertEquals(expBytes, ctx.numBytesInFeeScheduleRepr());
	}

	@Test
	void returnsExpectedCtxForAccount() {
		var mockInfo = mock(AccountInfo.class);
		var mockTimeStamp = mock(Timestamp.class);
		given(workingView.infoForAccount(any(), any())).willReturn(Optional.ofNullable(mockInfo));
		given(mockInfo.getKey()).willReturn(key);
		given(mockInfo.getMemo()).willReturn(memo);
		given(mockInfo.getExpirationTime()).willReturn(mockTimeStamp);
		given(mockTimeStamp.getSeconds()).willReturn(now);
		given(mockInfo.getTokenRelationshipsCount()).willReturn(tokenRelationShipCount);
		given(mockInfo.getMaxAutomaticTokenAssociations()).willReturn(maxAutomaticAssociations);
		given(mockInfo.hasProxyAccountID()).willReturn(true);

		final var ctx = subject.ctxForCryptoUpdate(TransactionBody.getDefaultInstance());

		assertEquals(memo, ctx.currentMemo());
		assertEquals(maxAutomaticAssociations, ctx.currentMaxAutomaticAssociations());
		assertEquals(now, ctx.currentExpiry());

	}

	@Test
	void returnsMissingCtxWhenAccountNotFound() {
		given(workingView.infoForAccount(any(), any())).willReturn(Optional.empty());

		final var ctx = subject.ctxForCryptoUpdate(TransactionBody.getDefaultInstance());

		assertEquals(DEFAULT_MEMO, ctx.currentMemo());
		assertEquals(0, ctx.currentExpiry());
	}

	@Test
	void getMetaForTokenMintWorks() {
		TokenMintTransactionBody mintTxnBody = getUniqueTokenMintOp();
		TransactionBody txn = getTxnBody(mintTxnBody);

		given(accessor.getTxn()).willReturn(txn);
		given(accessor.getSubType()).willReturn(TOKEN_NON_FUNGIBLE_UNIQUE);
		Optional<TokenType> tokenType = Optional.of(TokenType.NON_FUNGIBLE_UNIQUE);
		given(workingView.tokenWith(target)).willReturn(Optional.of(extant));

		final var tokenMintMeta = subject.metaForTokenMint(accessor);

		// then:
		assertEquals(34, tokenMintMeta.getBpt());
		assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, tokenMintMeta.getSubType());
		assertEquals(12345670, tokenMintMeta.getRbs());
		assertEquals(80, tokenMintMeta.getTransferRecordDb());
	}

	@Test
	void getMetaForTokenBurnWorks() {
		TokenBurnTransactionBody burnTxnBody = getFungibleCommonTokenBurnOp();
		TransactionBody txn = getTxnBody(burnTxnBody);

		given(accessor.getTxn()).willReturn(txn);
		given(accessor.getSubType()).willReturn(TOKEN_FUNGIBLE_COMMON);

		final var tokenBurnMeta = subject.metaForTokenBurn(accessor);

		// then:
		assertEquals(32, tokenBurnMeta.getBpt());
		assertEquals(SubType.TOKEN_FUNGIBLE_COMMON, tokenBurnMeta.getSubType());
		assertEquals(0, tokenBurnMeta.getSerialNumsCount());
		assertEquals(56, tokenBurnMeta.getTransferRecordDb());
	}

	@Test
	void getMetaForTokenWipeWorks() {
		TokenWipeAccountTransactionBody wipeTxnBody = getUniqueTokenWipeOp();
		TransactionBody txn = getTxnBody(wipeTxnBody);

		given(accessor.getTxn()).willReturn(txn);
		given(accessor.getSubType()).willReturn(TOKEN_NON_FUNGIBLE_UNIQUE);

		final var tokenWipeMeta = subject.metaForTokenWipe(accessor);

		// then:
		assertEquals(32, tokenWipeMeta.getBpt());
		assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, tokenWipeMeta.getSubType());
		assertEquals(1, tokenWipeMeta.getSerialNumsCount());
		assertEquals(80, tokenWipeMeta.getTransferRecordDb());
	}

	private TokenFeeScheduleUpdateTransactionBody op() {
		return TokenFeeScheduleUpdateTransactionBody.newBuilder()
				.setTokenId(target)
				.build();
	}

	private List<FcCustomFee> fcFees() {
		final var collector = new EntityId(1, 2, 3);
		final var aDenom = new EntityId(2, 3, 4);
		final var bDenom = new EntityId(3, 4, 5);

		return List.of(
				fixedFee(1, null, collector),
				fixedFee(2, aDenom, collector),
				fixedFee(2, bDenom, collector),
				fractionalFee(
						1, 2, 1, 2, false, collector),
				fractionalFee(
						1, 3, 1, 2, false, collector),
				fractionalFee(
						1, 4, 1, 2, false, collector),
				royaltyFee(1, 10, null, collector),
				royaltyFee(1, 10,
						new FixedFeeSpec(1, null), collector),
				royaltyFee(1, 10,
						new FixedFeeSpec(1, aDenom), collector)
		);
	}


	private TokenMintTransactionBody getUniqueTokenMintOp() {
		return TokenMintTransactionBody.newBuilder()
				.setToken(target)
				.addAllMetadata(List.of(
						ByteString.copyFromUtf8("NFT meta 1")
				)).build();
	}

	private TokenBurnTransactionBody getFungibleCommonTokenBurnOp() {
		return TokenBurnTransactionBody.newBuilder()
				.setToken(target)
				.setAmount(1000L)
				.build();
	}

	private TokenWipeAccountTransactionBody getUniqueTokenWipeOp() {
		return TokenWipeAccountTransactionBody.newBuilder()
				.setToken(target)
				.addAllSerialNumbers(List.of(1L))
				.build();
	}

	private TransactionBody getTxnBody(final TokenMintTransactionBody op) {
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenMint(op)
				.build();
	}

	private TransactionBody getTxnBody(final TokenBurnTransactionBody op) {
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenBurn(op)
				.build();
	}
	private TransactionBody getTxnBody(final TokenWipeAccountTransactionBody op) {
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenWipe(op)
				.build();
	}

	private final int newFileBytes = 1234;
	private final long now = 1_234_567L;
	private final long then = 1_234_567L + 7776000L;
	private final FileID targetFile = IdUtils.asFile("0.0.123456");
	private final FileID specialFile = IdUtils.asFile("0.0.159");
	private final Key key = Key.newBuilder()
			.setEd25519(ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
			.build();
	private final JKeyList wacl = new JKeyList(List.of(
			new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8))));
	private final MerkleToken extant = new MerkleToken(now, 1, 2,
			"shLong.asPhlThree", "FOUR", false, true,
			EntityId.MISSING_ENTITY_ID);
	private final String memo = "accountInfo";
	private final int tokenRelationShipCount = 23;
	private final int maxAutomaticAssociations = 12;
	private final TokenID target = IdUtils.asToken("0.0.1003");
	private final TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();
	private final HFileMeta fileMeta = new HFileMeta(false, wacl, then);
	private final TransactionBody stdAppendTxn = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder()
					.setTransactionValidStart(Timestamp.newBuilder()
							.setSeconds(now)))
			.setFileAppend(
					FileAppendTransactionBody.newBuilder()
							.setFileID(targetFile)
							.setContents(ByteString.copyFrom(new byte[newFileBytes])))
			.build();
	private final TransactionBody specialAppendTxn = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder()
					.setTransactionValidStart(Timestamp.newBuilder()
							.setSeconds(now)))
			.setFileAppend(
					FileAppendTransactionBody.newBuilder()
							.setFileID(specialFile)
							.setContents(ByteString.copyFrom(new byte[newFileBytes])))
			.build();
}
