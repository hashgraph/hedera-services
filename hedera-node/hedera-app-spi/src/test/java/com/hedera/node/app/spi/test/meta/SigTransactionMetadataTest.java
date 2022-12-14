/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.node.app.spi.test.meta;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.node.app.spi.test.meta.SigTransactionMetadataBuilderTest.A_COMPLEX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SigTransactionMetadataTest {
	private static final AccountID PAYER = AccountID.newBuilder().setAccountNum(3L).build();
	@Mock
	private HederaKey payerKey;
	@Mock
	private HederaKey otherKey;
	@Mock
	AccountKeyLookup lookup;
	private SigTransactionMetadata subject;

	@Test
	void gettersWork() {
		given(lookup.getKey(PAYER)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));
		final var txn = createAccountTransaction();
		subject =
				new SigTransactionMetadataBuilder(lookup)
						.payerKeyFor(PAYER)
						.txnBody(txn)
						.addToReqKeys(otherKey)
						.build();

		assertFalse(subject.failed());
		assertEquals(txn, subject.txnBody());
		assertEquals(ResponseCodeEnum.OK, subject.status());
		assertEquals(payerKey, subject.payerKey());
		assertEquals(List.of(otherKey), subject.requiredNonPayerKeys());
	}

	@Test
	void gettersWorkOnFailure() {
		given(lookup.getKey(PAYER)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));
		final var txn = createAccountTransaction();
		subject =
				new SigTransactionMetadataBuilder(lookup)
						.payerKeyFor(PAYER)
						.status(INVALID_ACCOUNT_ID)
						.txnBody(txn)
						.addToReqKeys(otherKey)
						.build();

		assertTrue(subject.failed());
		assertEquals(txn, subject.txnBody());
		assertEquals(INVALID_ACCOUNT_ID, subject.status());
		assertEquals(payerKey, subject.payerKey());
		assertEquals(
				List.of(),
				subject.requiredNonPayerKeys()); // otherKey is not added as there is failure
		// status set
	}

	private TransactionBody createAccountTransaction() {
		final var transactionID =
				TransactionID.newBuilder()
						.setAccountID(PAYER)
						.setTransactionValidStart(
								Timestamp.newBuilder().setSeconds(123_456L).build());
		final var createTxnBody =
				CryptoCreateTransactionBody.newBuilder()
						.setKey(A_COMPLEX_KEY)
						.setReceiverSigRequired(true)
						.setMemo("Create Account")
						.build();
		return TransactionBody.newBuilder()
				.setTransactionID(transactionID)
				.setCryptoCreateAccount(createTxnBody)
				.build();
	}
}
