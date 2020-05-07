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
import com.goterl.lazycode.lazysodium.interfaces.Sign;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.MatcherAssert.assertThat;
import static com.hedera.services.utils.PlatformTxnAccessor.uncheckedAccessorFor;

@RunWith(JUnitPlatform.class)
public class PlatformTxnAccessorTest {
	private static final byte[] NONSENSE = "Jabberwocky".getBytes();
	private static final TransactionBody A_PARSEABLE_TXN = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder().setAccountID(asAccount("0.0.2")))
			.setMemo("Hi!")
			.build();

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
	public void usesBodyCorrectly() throws Exception {
		// given:
		Transaction signedTxnWithBody = Transaction.newBuilder()
				.setBody(A_PARSEABLE_TXN)
				.build();
		com.swirlds.common.Transaction platformTxn =
				new com.swirlds.common.Transaction(signedTxnWithBody.toByteArray());

		// when:
		PlatformTxnAccessor subject = new PlatformTxnAccessor(platformTxn);

		// then:
		assertEquals(A_PARSEABLE_TXN, subject.getTxn());
		assertThat(List.of(subject.getTxnBytes()), contains(A_PARSEABLE_TXN.toByteArray()));
	}

	@Test
	public void usesBodyBytesCorrectly() throws Exception {
		// given:
		Transaction signedTxnWithBody = Transaction.newBuilder()
				.setBodyBytes(A_PARSEABLE_TXN.toByteString())
				.build();
		com.swirlds.common.Transaction platformTxn =
				new com.swirlds.common.Transaction(signedTxnWithBody.toByteArray());

		// when:
		PlatformTxnAccessor subject = new PlatformTxnAccessor(platformTxn);

		// then:
		assertEquals(A_PARSEABLE_TXN, subject.getTxn());
		assertThat(List.of(subject.getTxnBytes()), contains(A_PARSEABLE_TXN.toByteArray()));
	}

	@Test
	public void getsCorrectLoggableForm() throws Exception {
		Transaction signedTxnWithBody = Transaction.newBuilder()
				.setBodyBytes(A_PARSEABLE_TXN.toByteString())
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
				.setBodyBytes(signedTxn4Log.getBody().toByteString())
				.clearBody()
				.build();

		// then:
		assertEquals(ByteString.EMPTY, signedTxn4Log.getBodyBytes());
		assertEquals(A_PARSEABLE_TXN, signedTxn4Log.getBody());
		assertEquals(signedTxnWithBody, asBodyBytes);
	}

	@Test
	public void getsPayer() throws Exception {
		// given:
		AccountID payer = asAccount("0.0.2");
		Transaction signedTxnWithBody = Transaction.newBuilder()
				.setBodyBytes(A_PARSEABLE_TXN.toByteString())
				.build();
		com.swirlds.common.Transaction platformTxn =
				new com.swirlds.common.Transaction(signedTxnWithBody.toByteArray());

		// when:
		PlatformTxnAccessor subject = new PlatformTxnAccessor(platformTxn);

		// then:
		assertEquals(payer, subject.getPayer());
	}
}
