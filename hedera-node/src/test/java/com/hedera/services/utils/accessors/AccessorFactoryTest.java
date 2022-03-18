package com.hedera.services.utils.accessors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAdjustAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAdjustAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AccessorFactoryTest {
	@Mock
	private AliasManager aliasManager;

	AccessorFactory subject;

	private final AccountID payer = asAccount("1.2.345");
	private final TransactionID txnId = TransactionID.newBuilder().setAccountID(payer).build();
	private final TransactionBody someTxn = TransactionBody.newBuilder()
			.setTransactionID(txnId)
			.setMemo("Hi!")
			.build();
	private final TransactionBody tokenWipeTxn = TransactionBody.newBuilder()
			.setTransactionID(txnId)
			.setTokenWipe(TokenWipeAccountTransactionBody.getDefaultInstance())
			.setMemo("Hi!")
			.build();
	private final TransactionBody cryptoCreateTxn = TransactionBody.newBuilder()
			.setTransactionID(txnId)
			.setCryptoCreateAccount(CryptoCreateTransactionBody.newBuilder()
					.setInitialBalance(100L)
					.setMemo("Not all those who wander are lost")
					.build())
			.build();
	private final TransactionBody cryptoDeleteTxn = TransactionBody.newBuilder()
			.setTransactionID(txnId)
			.setCryptoDelete(CryptoDeleteTransactionBody.getDefaultInstance())
			.build();
	private final TransactionBody cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
			.setTransactionID(txnId)
			.setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.getDefaultInstance())
			.build();
	private final TransactionBody cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
			.setTransactionID(txnId)
			.setCryptoAdjustAllowance(CryptoAdjustAllowanceTransactionBody.getDefaultInstance())
			.build();

	@BeforeEach
	void setUp() {
		subject = new AccessorFactory(aliasManager);
	}

	@Test
	void constructsCorrectly() throws InvalidProtocolBufferException {
		final var platformTxn =
				new SwirldTransaction(Transaction.newBuilder()
						.setBodyBytes(someTxn.toByteString())
						.build().toByteArray());
		assertTrue(subject.nonTriggeredTxn(platformTxn.getContentsDirect()) instanceof SignedTxnAccessor);

		final var wipeTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(tokenWipeTxn.toByteString())
				.build().toByteArray());
		assertTrue(subject.nonTriggeredTxn(wipeTxn.getContentsDirect()) instanceof TokenWipeAccessor);

		final var accountCreateTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(cryptoCreateTxn.toByteString())
				.build().toByteArray());
		assertTrue(subject.nonTriggeredTxn(accountCreateTxn.getContentsDirect()) instanceof CryptoCreateAccessor);

		final var accountDeleteTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(cryptoDeleteTxn.toByteString())
				.build().toByteArray());
		assertTrue(subject.nonTriggeredTxn(accountDeleteTxn.getContentsDirect()) instanceof  CryptoDeleteAccessor);

		final var approveAllowanceTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(cryptoApproveAllowanceTxn.toByteString())
				.build().toByteArray());
		final var approveAllowanceAccessor = subject.nonTriggeredTxn(approveAllowanceTxn.getContentsDirect());
		assertTrue(approveAllowanceAccessor instanceof CryptoAllowanceAccessor);
		assertEquals(CryptoApproveAllowance, approveAllowanceAccessor.getFunction());

		final var adjustAllowanceTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(cryptoAdjustAllowanceTxn.toByteString())
				.build().toByteArray());
		final var adjustAllowanceAccessor = subject.nonTriggeredTxn(adjustAllowanceTxn.getContentsDirect());
		assertTrue(adjustAllowanceAccessor instanceof  CryptoAllowanceAccessor);
		assertEquals(CryptoAdjustAllowance, adjustAllowanceAccessor.getFunction());
	}
}
