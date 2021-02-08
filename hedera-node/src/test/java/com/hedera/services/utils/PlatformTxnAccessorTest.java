package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.BDDMockito.*;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.MatcherAssert.assertThat;
import static com.hedera.services.utils.PlatformTxnAccessor.uncheckedAccessorFor;
import static org.mockito.Mockito.mock;

public class PlatformTxnAccessorTest {
	private static final byte[] NONSENSE = "Jabberwocky".getBytes();
	TransactionBody someTxn = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder().setAccountID(asAccount("0.0.2")))
			.setMemo("Hi!")
			.build();

	@Test
	public void extractorReturnsNoneWhenExpected() {
		// expect:
		assertEquals(HederaFunctionality.NONE, SignedTxnAccessor.functionExtractor.apply(someTxn));
	}

	@Test
	public void hasExpectedSignedBytes() throws InvalidProtocolBufferException {
		// given:
		Transaction signedTxnWithBody = Transaction.newBuilder()
				.setBodyBytes(someTxn.toByteString())
				.build();

		// when:
		SignedTxnAccessor subject = new SignedTxnAccessor(signedTxnWithBody);

		// then:
		assertArrayEquals(signedTxnWithBody.toByteArray(), subject.getBackwardCompatibleSignedTxnBytes());
	}

	@Test
	public void extractorReturnsExpectedFunction() {
		// given:
		someTxn = someTxn.toBuilder()
				.setConsensusCreateTopic(ConsensusCreateTopicTransactionBody.newBuilder())
				.build();

		// expect:
		assertEquals(ConsensusCreateTopic, SignedTxnAccessor.functionExtractor.apply(someTxn));
	}

	@Test
	public void usesExtractorToGetFunctionAsExpected() {
		// setup:
		var memory = SignedTxnAccessor.functionExtractor;
		Function<TransactionBody, HederaFunctionality> mockFn =
				(Function<TransactionBody, HederaFunctionality>)mock(Function.class);
		SignedTxnAccessor.functionExtractor = mockFn;
		// and:
		someTxn = someTxn.toBuilder()
				.setConsensusCreateTopic(ConsensusCreateTopicTransactionBody.newBuilder())
				.build();
		Transaction signedTxn = Transaction.newBuilder()
				.setBodyBytes(someTxn.toByteString())
				.build();

		given(mockFn.apply(any())).willReturn(ConsensusCreateTopic);
		var subject = SignedTxnAccessor.uncheckedFrom(signedTxn);

		// when:
		var first = subject.getFunction();
		var second = subject.getFunction();

		// then:
		assertEquals(ConsensusCreateTopic, first);
		assertEquals(second, first);
		// and:
		verify(mockFn, times(1)).apply(any());

		// cleanup:
		SignedTxnAccessor.functionExtractor = memory;
	}

	@Test
	public void allowsUncheckedConstruction() {
		// setup:
		Transaction validTxn = Transaction.getDefaultInstance();

		// expect:
		assertDoesNotThrow(() -> SignedTxnAccessor.uncheckedFrom(validTxn));
		assertDoesNotThrow(() -> SignedTxnAccessor.uncheckedFrom(null));
	}

	@Test
	public void failsWithIllegalStateOnUncheckedConstruction() {
		// expect:
		assertThrows(IllegalStateException.class, () ->
				uncheckedAccessorFor(new com.swirlds.common.Transaction(NONSENSE)));
	}

	@Test
	public void failsOnInvalidSignedTxn() {
		// given:
		com.swirlds.common.Transaction platformTxn = new com.swirlds.common.Transaction(NONSENSE);

		// expect:
		assertThrows(InvalidProtocolBufferException.class, () -> new PlatformTxnAccessor(platformTxn));
	}

	@Test
	public void failsOnInvalidTxn() {
		// given:
		Transaction signedNonsenseTxn = Transaction.newBuilder()
				.setBodyBytes(ByteString.copyFrom(NONSENSE))
				.build();
		// and:
		com.swirlds.common.Transaction platformTxn =
				new com.swirlds.common.Transaction(signedNonsenseTxn.toByteArray());

		// expect:
		assertThrows(InvalidProtocolBufferException.class, () -> new PlatformTxnAccessor(platformTxn));
	}

	@Test
	public void usesBodyBytesCorrectly() throws Exception {
		// given:
		Transaction signedTxnWithBody = Transaction.newBuilder()
				.setBodyBytes(someTxn.toByteString())
				.build();
		com.swirlds.common.Transaction platformTxn =
				new com.swirlds.common.Transaction(signedTxnWithBody.toByteArray());

		// when:
		PlatformTxnAccessor subject = new PlatformTxnAccessor(platformTxn);

		// then:
		assertEquals(someTxn, subject.getTxn());
		assertThat(List.of(subject.getTxnBytes()), contains(someTxn.toByteArray()));
	}

	@Test
	public void getsCorrectLoggableForm() throws Exception {
		Transaction signedTxnWithBody = Transaction.newBuilder()
				.setBodyBytes(someTxn.toByteString())
				.setSigMap(SignatureMap.newBuilder().addSigPair(
						SignaturePair.newBuilder()
								.setPubKeyPrefix(ByteString.copyFrom("UNREAL".getBytes()))
								.setEd25519(ByteString.copyFrom("FAKE".getBytes()))
				)).build();
		com.swirlds.common.Transaction platformTxn =
				new com.swirlds.common.Transaction(signedTxnWithBody.toByteArray());

		// when:
		PlatformTxnAccessor subject = new PlatformTxnAccessor(platformTxn);
		Transaction signedTxn4Log = subject.getSignedTxn4Log();
		Transaction asBodyBytes = signedTxn4Log
				.toBuilder()
				.setBodyBytes(CommonUtils.extractTransactionBodyByteString(signedTxn4Log))
				.build();

		// then:
		assertEquals(someTxn, CommonUtils.extractTransactionBody(signedTxn4Log));
		assertEquals(signedTxnWithBody, asBodyBytes);
	}

	@Test
	public void getsCorrectLoggableFormWithSignedTransactionBytes() throws Exception {
		SignedTransaction signedTxn = SignedTransaction.newBuilder().
				setBodyBytes(someTxn.toByteString()).
				setSigMap(SignatureMap.newBuilder().addSigPair(SignaturePair.newBuilder()
						.setPubKeyPrefix(ByteString.copyFrom("UNREAL".getBytes()))
						.setEd25519(ByteString.copyFrom("FAKE".getBytes())).build())).build();

		Transaction txn = Transaction.newBuilder().
				setSignedTransactionBytes(signedTxn.toByteString()).build();

		com.swirlds.common.Transaction platformTxn =
				new com.swirlds.common.Transaction(txn.toByteArray());

		// when:
		PlatformTxnAccessor subject = new PlatformTxnAccessor(platformTxn);
		Transaction signedTxn4Log = subject.getSignedTxn4Log();

		ByteString signedTxnBytes = signedTxn4Log.getSignedTransactionBytes();
		Transaction asBodyBytes = signedTxn4Log
				.toBuilder()
				.setSignedTransactionBytes(CommonUtils.extractTransactionBodyByteString(signedTxn4Log))
				.build();

		// then:
		assertEquals(signedTxnBytes, txn.getSignedTransactionBytes());
		assertEquals(signedTxn.getBodyBytes(), asBodyBytes.getSignedTransactionBytes());
	}

	@Test
	public void getsPayer() throws Exception {
		// given:
		AccountID payer = asAccount("0.0.2");
		Transaction signedTxnWithBody = Transaction.newBuilder()
				.setBodyBytes(someTxn.toByteString())
				.build();
		com.swirlds.common.Transaction platformTxn =
				new com.swirlds.common.Transaction(signedTxnWithBody.toByteArray());

		// when:
		PlatformTxnAccessor subject = new PlatformTxnAccessor(platformTxn);

		// then:
		assertEquals(payer, subject.getPayer());
	}
}
