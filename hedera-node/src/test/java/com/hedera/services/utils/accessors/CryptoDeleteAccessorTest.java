package com.hedera.services.utils.accessors;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoDeleteAccessorTest {
	@Mock
	AliasManager aliasManager;

	private final long targetNum = 567;
	private final long transferAccountNum = 678L;
	private final EntityNum targetEntity = EntityNum.fromLong(targetNum);
	private final EntityNum transferAccountEntity = EntityNum.fromLong(transferAccountNum);
	private final AccountID payer = asAccount("0.0.2");
	private final AccountID target = asAccount("0.0." + targetNum);
	private final AccountID aliasedTarget = asAccountWithAlias("It is useless to meet revenge with revenge");
	private final AccountID transferAccount = asAccount("0.0." + transferAccountNum);
	private final AccountID aliasedTransferAccount = asAccountWithAlias("It will heal nothing");

	private CryptoDeleteAccessor subject;
	private SwirldTransaction accountDeleteTxn;

	@Test
	void fetchesDataAsExpected() throws InvalidProtocolBufferException {
		setUpWith(target, transferAccount);
		when(aliasManager.unaliased(target)).thenReturn(targetEntity);
		when(aliasManager.unaliased(transferAccount)).thenReturn(transferAccountEntity);

		subject = new CryptoDeleteAccessor(accountDeleteTxn.getContentsDirect(), aliasManager);

		validate();
	}

	@Test
	void fetchesAliasedDataAsExpected() throws InvalidProtocolBufferException {
		setUpWith(aliasedTarget, aliasedTransferAccount);
		when(aliasManager.unaliased(aliasedTarget)).thenReturn(targetEntity);
		when(aliasManager.unaliased(aliasedTransferAccount)).thenReturn(transferAccountEntity);

		subject = new CryptoDeleteAccessor(accountDeleteTxn.getContentsDirect(), aliasManager);

		validate();
	}

	@Test
	void fetchesMissingAliasAsExpected() throws InvalidProtocolBufferException {
		setUpWith(aliasedTarget, aliasedTransferAccount);
		when(aliasManager.unaliased(aliasedTarget)).thenReturn(EntityNum.MISSING_NUM);
		when(aliasManager.unaliased(aliasedTransferAccount)).thenReturn(EntityNum.MISSING_NUM);

		subject = new CryptoDeleteAccessor(accountDeleteTxn.getContentsDirect(), aliasManager);

		assertEquals(0L, subject.getTarget().getAccountNum());
		assertEquals(0L, subject.getTransferAccount().getAccountNum());
	}

	private void validate() {
		assertEquals(target, subject.getTarget());
		assertEquals(transferAccount, subject.getTransferAccount());
	}

	private void setUpWith(AccountID target, AccountID transferAccount) {
		final var txnBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(payer).build())
				.setCryptoDelete(CryptoDeleteTransactionBody.newBuilder()
						.setDeleteAccountID(target)
						.setTransferAccountID(transferAccount)
						.build())
				.build();
		accountDeleteTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(txnBody.toByteString())
				.build().toByteArray());
	}

}
