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
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TokenWipeAccessorTest {
	private TokenWipeAccessor accessor;

	@Mock
	private AliasManager aliasManager;

	private AccountID idToWipe = asAccount("0.0.4");
	private TokenID token = asToken("0.0.3");
	private AccountID payer = asAccount("0.0.2");
	private AccountID idToWipeAlias = asAccountWithAlias("dummy");

	private SwirldTransaction wipeTxn;

	@Test
	void getsMetaCorrectly() throws InvalidProtocolBufferException {
		final TransactionBody tokenWipeTxn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(payer))
				.setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
						.setToken(token)
						.setAccount(idToWipe)
						.setAmount(100L)
						.addAllSerialNumbers(List.of(1L)))
				.setMemo("Hi!")
				.build();
		givenTxn(tokenWipeTxn);
		accessor = new TokenWipeAccessor(wipeTxn, aliasManager);

		given(aliasManager.unaliased(idToWipe)).willReturn(EntityNum.fromAccountId(idToWipe));
		assertEquals(Id.fromGrpcAccount(idToWipe), accessor.accountToWipe());
		assertEquals(List.of(1L), accessor.serialNums());
		assertEquals(100L, accessor.amount());
		assertEquals(Id.fromGrpcToken(token), accessor.targetToken());
		assertEquals(payer, accessor.getPayer());
	}

	@Test
	void looksUpAliasCorrectly() throws InvalidProtocolBufferException {
		TransactionBody tokenWipeAliasedTxn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(payer))
				.setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
						.setToken(token)
						.setAccount(idToWipeAlias)
						.setAmount(100L)
						.addAllSerialNumbers(List.of(1L)))
				.setMemo("Hi!")
				.build();
		givenTxn(tokenWipeAliasedTxn);
		accessor = new TokenWipeAccessor(wipeTxn, aliasManager);

		given(aliasManager.unaliased(idToWipeAlias)).willReturn(EntityNum.fromAccountId(idToWipe));
		assertEquals(Id.fromGrpcAccount(idToWipe), accessor.accountToWipe());
		assertEquals(List.of(1L), accessor.serialNums());
		assertEquals(100L, accessor.amount());
		assertEquals(Id.fromGrpcToken(token), accessor.targetToken());
		assertEquals(payer, accessor.getPayer());
	}

	void givenTxn(TransactionBody body) {
		wipeTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(body.toByteString())
				.build().toByteArray());
	}
}
