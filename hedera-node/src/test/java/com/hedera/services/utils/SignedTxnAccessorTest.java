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
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		assertArrayEquals(zeroByteMemoUtf8Bytes, accessor.getMemoUtf8Bytes());
		assertTrue(accessor.memoHasZeroByte());
		assertEquals(FeeBuilder.getSignatureCount(accessor.getSignedTxnWrapper()), accessor.numSigPairs());
		assertEquals(FeeBuilder.getSignatureSize(accessor.getSignedTxnWrapper()), accessor.sigMapSize());
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
}
