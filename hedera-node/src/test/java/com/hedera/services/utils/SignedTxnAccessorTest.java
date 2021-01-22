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

import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SignedTxnAccessorTest {
	@Test
	public void parseCorrectly() throws Exception {
		Transaction transaction = RequestBuilder.getCryptoTransferRequest(1234l, 0l, 0l,
				3l, 0l, 0l,
				100_000_000l,
				Timestamp.getDefaultInstance(),
				Duration.getDefaultInstance(),
				false,
				"test memo",
				5678l, -70000l,
				5679l, 70000l);
		TransactionBody body = CommonUtils.extractTransactionBody(transaction);
		SignedTxnAccessor accessor = SignedTxnAccessor.uncheckedFrom(transaction);

		assertEquals(transaction, accessor.getSignedTxn());
		assertEquals(transaction, accessor.getSignedTxn4Log());
		assertArrayEquals(transaction.toByteArray(), accessor.getSignedTxnBytes());
		assertEquals(body, accessor.getTxn());
		assertArrayEquals(body.toByteArray(), accessor.getTxnBytes());
		assertEquals(body.getTransactionID(), accessor.getTxnId());
		assertEquals(1234l, accessor.getPayer().getAccountNum());
		assertEquals(HederaFunctionality.CryptoTransfer, accessor.getFunction());
		assertArrayEquals(CommonUtils.noThrowSha384HashOf(transaction.toByteArray()), accessor.getHash().toByteArray());
	}

	@Test
	public void parseNewTransactionCorrectly() throws Exception {
		Transaction transaction = RequestBuilder.getCryptoTransferRequest(1234l, 0l, 0l,
				3l, 0l, 0l,
				100_000_000l,
				Timestamp.getDefaultInstance(),
				Duration.getDefaultInstance(),
				false,
				"test memo",
				5678l, -70000l,
				5679l, 70000l);
		TransactionBody body = CommonUtils.extractTransactionBody(transaction);
		SignedTransaction signedTransaction = SignedTransaction.newBuilder()
				.setBodyBytes(body.toByteString())
				.setSigMap(SignatureMap.getDefaultInstance())
				.build();
		Transaction newTransaction = Transaction.newBuilder()
				.setSignedTransactionBytes(signedTransaction.toByteString())
				.build();
		SignedTxnAccessor accessor = SignedTxnAccessor.uncheckedFrom(newTransaction);

		assertEquals(newTransaction, accessor.getSignedTxn());
		assertEquals(newTransaction, accessor.getSignedTxn4Log());
		assertArrayEquals(newTransaction.toByteArray(), accessor.getSignedTxnBytes());
		assertEquals(body, accessor.getTxn());
		assertArrayEquals(body.toByteArray(), accessor.getTxnBytes());
		assertEquals(body.getTransactionID(), accessor.getTxnId());
		assertEquals(1234l, accessor.getPayer().getAccountNum());
		assertEquals(HederaFunctionality.CryptoTransfer, accessor.getFunction());
		assertArrayEquals(CommonUtils.noThrowSha384HashOf(signedTransaction.toByteArray()), accessor.getHash().toByteArray());
	}
}
