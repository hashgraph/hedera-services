package com.hedera.services.utils;

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
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.services.state.submerkle.FcCustomFee.fractionalFee;
import static com.hedera.test.utils.IdUtils.asAccount;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

class SignedTxnAccessorTest {
	private final String memo = "Eternal sunshine of the spotless mind";
	private final String zeroByteMemo = "Eternal s\u0000nshine of the spotless mind";
	private final byte[] memoUtf8Bytes = memo.getBytes();
	private final byte[] zeroByteMemoUtf8Bytes = zeroByteMemo.getBytes();

	private final SignatureMap expectedMap = SignatureMap.newBuilder()
			.addSigPair(SignaturePair.newBuilder()
					.setPubKeyPrefix(ByteString.copyFromUtf8("f"))
					.setEd25519(ByteString.copyFromUtf8("irst")))
			.addSigPair(SignaturePair.newBuilder()
					.setPubKeyPrefix(ByteString.copyFromUtf8("s"))
					.setEd25519(ByteString.copyFromUtf8("econd")))
			.build();

	@Test
	void unsupportedOpsThrowByDefault() {
		// setup:
		final var subject = mock(TxnAccessor.class);

		// given:
		doCallRealMethod().when(subject).setSigMeta(any());
		doCallRealMethod().when(subject).getSigMeta();
		doCallRealMethod().when(subject).getPkToSigsFn();
		doCallRealMethod().when(subject).baseUsageMeta();
		doCallRealMethod().when(subject).availXferUsageMeta();
		doCallRealMethod().when(subject).availSubmitUsageMeta();
		doCallRealMethod().when(subject).getSpanMap();

		// expect:
		assertThrows(UnsupportedOperationException.class, subject::getSigMeta);
		assertThrows(UnsupportedOperationException.class, subject::getPkToSigsFn);
		assertThrows(UnsupportedOperationException.class, subject::baseUsageMeta);
		assertThrows(UnsupportedOperationException.class, subject::availXferUsageMeta);
		assertThrows(UnsupportedOperationException.class, subject::availSubmitUsageMeta);
		assertThrows(UnsupportedOperationException.class, subject::getSpanMap);
		assertThrows(UnsupportedOperationException.class, () -> subject.setSigMeta(null));
	}

	@Test
	void uncheckedPropagatesIaeOnNonsense() {
		// setup:
		final var nonsenseTxn = Transaction.newBuilder()
				.setSignedTransactionBytes(ByteString.copyFromUtf8("NONSENSE"))
				.build();

		// expect:
		Assertions.assertThrows(IllegalArgumentException.class, () -> SignedTxnAccessor.uncheckedFrom(nonsenseTxn));
	}

	@Test
	void parsesLegacyCorrectly() throws Exception {
		// setup:
		final long offeredFee = 100_000_000L;
		Transaction transaction = RequestBuilder.getCryptoTransferRequest(1234l, 0l, 0l,
				3l, 0l, 0l,
				offeredFee,
				Timestamp.getDefaultInstance(),
				Duration.getDefaultInstance(),
				false,
				zeroByteMemo,
				5678l, -70000l,
				5679l, 70000l);
		transaction = transaction.toBuilder()
				.setSigMap(expectedMap)
				.build();
		TransactionBody body = CommonUtils.extractTransactionBody(transaction);

		// given:
		SignedTxnAccessor accessor = SignedTxnAccessor.uncheckedFrom(transaction);
		// and:
		final var txnUsageMeta = accessor.baseUsageMeta();

		assertEquals(transaction, accessor.getSignedTxnWrapper());
		assertArrayEquals(transaction.toByteArray(), accessor.getSignedTxnWrapperBytes());
		assertEquals(body, accessor.getTxn());
		assertArrayEquals(body.toByteArray(), accessor.getTxnBytes());
		assertEquals(body.getTransactionID(), accessor.getTxnId());
		assertEquals(1234l, accessor.getPayer().getAccountNum());
		assertEquals(HederaFunctionality.CryptoTransfer, accessor.getFunction());
		assertEquals(offeredFee, accessor.getOfferedFee());
		assertArrayEquals(CommonUtils.noThrowSha384HashOf(transaction.toByteArray()), accessor.getHash());
		assertEquals(expectedMap, accessor.getSigMap());
		assertArrayEquals("irst".getBytes(), accessor.getPkToSigsFn().sigBytesFor("f".getBytes()));
		assertArrayEquals(zeroByteMemoUtf8Bytes, accessor.getMemoUtf8Bytes());
		assertTrue(accessor.memoHasZeroByte());
		assertEquals(FeeBuilder.getSignatureCount(accessor.getSignedTxnWrapper()), accessor.numSigPairs());
		assertEquals(FeeBuilder.getSignatureSize(accessor.getSignedTxnWrapper()), accessor.sigMapSize());
		assertEquals(zeroByteMemo, accessor.getMemo());
		// and:
		assertEquals(memoUtf8Bytes.length, txnUsageMeta.getMemoUtf8Bytes());
	}

	@Test
	void understandsFullXferUsageIncTokens() {
		// setup:
		final var txn = Transaction.newBuilder()
				.setBodyBytes(tokenXfers().toByteString())
				.build();

		// given:
		final var subject = SignedTxnAccessor.uncheckedFrom(txn);

		// when:
		final var xferMeta = subject.availXferUsageMeta();

		// then:
		assertEquals(1, xferMeta.getTokenMultiplier());
		assertEquals(3, xferMeta.getNumTokensInvolved());
		assertEquals(7, xferMeta.getNumTokenTransfers());
	}

	@Test
	void rejectsRequestForMetaIfNotAvail() {
		// setup:
		final var txn = Transaction.newBuilder()
				.setBodyBytes(TransactionBody.newBuilder()
						.setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance())
						.build().toByteString())
				.build();

		// given:
		final var subject = SignedTxnAccessor.uncheckedFrom(txn);

		// expect:
		assertThrows(IllegalStateException.class, subject::availXferUsageMeta);
		assertThrows(IllegalStateException.class, subject::availSubmitUsageMeta);
	}

	@Test
	void understandsSubmitMessageMeta() {
		// setup:
		final var message = "And after, arranged it in a song";
		final var txn = Transaction.newBuilder()
				.setBodyBytes(TransactionBody.newBuilder()
						.setConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody.newBuilder()
								.setMessage(ByteString.copyFromUtf8(message)))
						.build().toByteString())
				.build();

		// given:
		final var subject = SignedTxnAccessor.uncheckedFrom(txn);

		// when:
		final var submitMeta = subject.availSubmitUsageMeta();

		// then:
		assertEquals(message.length(), submitMeta.getNumMsgBytes());
	}

	@Test
	void parseNewTransactionCorrectly() throws Exception {
		Transaction transaction = RequestBuilder.getCryptoTransferRequest(
				1234l, 0l, 0l,
				3l, 0l, 0l,
				100_000_000l,
				Timestamp.getDefaultInstance(),
				Duration.getDefaultInstance(),
				false,
				memo,
				5678l, -70000l,
				5679l, 70000l);
		TransactionBody body = CommonUtils.extractTransactionBody(transaction);
		SignedTransaction signedTransaction = SignedTransaction.newBuilder()
				.setBodyBytes(body.toByteString())
				.setSigMap(expectedMap)
				.build();
		Transaction newTransaction = Transaction.newBuilder()
				.setSignedTransactionBytes(signedTransaction.toByteString())
				.build();
		SignedTxnAccessor accessor = SignedTxnAccessor.uncheckedFrom(newTransaction);

		assertEquals(newTransaction, accessor.getSignedTxnWrapper());
		assertArrayEquals(newTransaction.toByteArray(), accessor.getSignedTxnWrapperBytes());
		assertEquals(body, accessor.getTxn());
		assertArrayEquals(body.toByteArray(), accessor.getTxnBytes());
		assertEquals(body.getTransactionID(), accessor.getTxnId());
		assertEquals(1234l, accessor.getPayer().getAccountNum());
		assertEquals(HederaFunctionality.CryptoTransfer, accessor.getFunction());
		assertArrayEquals(CommonUtils.noThrowSha384HashOf(signedTransaction.toByteArray()),
				accessor.getHash());
		assertEquals(expectedMap, accessor.getSigMap());
		assertArrayEquals(memoUtf8Bytes, accessor.getMemoUtf8Bytes());
		assertFalse(accessor.memoHasZeroByte());
		assertEquals(FeeBuilder.getSignatureCount(accessor.getSignedTxnWrapper()), accessor.numSigPairs());
		assertEquals(FeeBuilder.getSignatureSize(accessor.getSignedTxnWrapper()), accessor.sigMapSize());
		assertEquals(memo, accessor.getMemo());
	}

	@Test
	void registersNoneOnMalformedCreation() throws Exception {
		// setup:
		var xferWithTopLevelBodyBytes = RequestBuilder.getCryptoTransferRequest(
				1234l, 0l, 0l,
				3l, 0l, 0l,
				100_000_000l,
				Timestamp.getDefaultInstance(),
				Duration.getDefaultInstance(),
				false,
				"test memo",
				5678l, -70000l,
				5679l, 70000l);

		// given:
		var body = TransactionBody.parseFrom(xferWithTopLevelBodyBytes.getBodyBytes());
		var confusedTxn = Transaction.parseFrom(body.toByteArray());

		// when:
		var confusedAccessor = SignedTxnAccessor.uncheckedFrom(confusedTxn);

		// then:
		assertEquals(HederaFunctionality.NONE, confusedAccessor.getFunction());
	}

	@Test
	void throwsOnUnsupportedCallToGetScheduleRef() {
		// given:
		var subject = SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance());

		// expect:
		Assertions.assertThrows(UnsupportedOperationException.class, subject::getScheduleRef);
	}

	@Test
	void setsFeeScheduleUpdateMeta() {
		// setup:
		final var txn = signedFeeScheduleUpdateTxn();
		final var expectedGrpcReprBytes =
				feeScheduleUpdateTxn().getTokenFeeScheduleUpdate().getSerializedSize()
						- feeScheduleUpdateTxn().getTokenFeeScheduleUpdate().getTokenId().getSerializedSize();
		final var tokenOpsUsage = new TokenOpsUsage();
		final var expectedReprBytes = tokenOpsUsage.bytesNeededToRepr(fees());
		final var spanMapAccessor = new ExpandHandleSpanMapAccessor();

		// given:
		final var accessor = SignedTxnAccessor.uncheckedFrom(txn);

		// when:
		final var expandedMeta = spanMapAccessor.getFeeScheduleUpdateMeta(accessor);

		// then:
		assertEquals(now, expandedMeta.effConsensusTime());
		assertEquals(expectedReprBytes, expandedMeta.numBytesInNewFeeScheduleRepr());
		assertEquals(expectedGrpcReprBytes, expandedMeta.numBytesInGrpcFeeScheduleRepr());
	}

	private Transaction signedFeeScheduleUpdateTxn() {
		return Transaction.newBuilder()
				.setSignedTransactionBytes(SignedTransaction.newBuilder()
						.setBodyBytes(feeScheduleUpdateTxn().toByteString())
						.build().toByteString())
				.build();
	}

	private TransactionBody feeScheduleUpdateTxn() {
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.newBuilder()
						.addAllCustomFees(fees()))
				.build();
	}

	private List<CustomFee> fees() {
		final var collector = new EntityId(1, 2 ,3);
		final var aDenom = new EntityId(2, 3 ,4);
		final var bDenom = new EntityId(3, 4 ,5);

		return List.of(
				fixedFee(1, null, collector),
				fixedFee(2, aDenom, collector),
				fixedFee(2, bDenom, collector),
				fractionalFee(1, 2, 1, 2, collector),
				fractionalFee(1, 3, 1, 2, collector),
				fractionalFee(1, 4, 1, 2, collector)
		).stream().map(FcCustomFee::asGrpc).collect(toList());
	}

	private TransactionBody tokenXfers() {
		var hbarAdjusts = TransferList.newBuilder()
				.addAccountAmounts(adjustFrom(a, -100))
				.addAccountAmounts(adjustFrom(b, 50))
				.addAccountAmounts(adjustFrom(c, 50))
				.build();
		final var op = CryptoTransferTransactionBody.newBuilder()
				.setTransfers(hbarAdjusts)
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -50),
								adjustFrom(b, 25),
								adjustFrom(c, 25)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anId)
						.addAllTransfers(List.of(
								adjustFrom(b, -100),
								adjustFrom(c, 100)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(yetAnotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -15),
								adjustFrom(b, 15)
						)))
				.build();

		return TransactionBody.newBuilder()
				.setMemo(memo)
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setCryptoTransfer(op)
				.build();
	}

	private AccountAmount adjustFrom(AccountID account, long amount) {
		return AccountAmount.newBuilder()
				.setAmount(amount)
				.setAccountID(account)
				.build();
	}

	private final long now = 1_234_567L;
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("2.3.4");
	private final AccountID c = asAccount("3.4.5");
	private final TokenID anId = IdUtils.asToken("0.0.75231");
	private final TokenID anotherId = IdUtils.asToken("0.0.75232");
	private final TokenID yetAnotherId = IdUtils.asToken("0.0.75233");
}
