package com.hedera.services.records;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionRecordServiceTest {
	@Mock
	private TransactionContext txnCtx;

	private TransactionRecordService subject;

	@BeforeEach
	void setUp() {
		subject = new TransactionRecordService(txnCtx);
	}

	@Test
	void updatesReceiptWithNewTotalSupply() {
		// setup:
		final var token = new Token(new Id(0, 0, 2));
		token.setTotalSupply(123L);

		// when:
		subject.includeChangesToToken(token);

		// then:
		verify(txnCtx).setNewTotalSupply(123L);
	}

	@Test
	void doesntUpdatesReceiptWithoutNewTotalSupply() {
		// setup:
		final var token = new Token(new Id(0, 0, 2));
		token.initTotalSupply(123L);

		// when:
		subject.includeChangesToToken(token);

		// then:
		verify(txnCtx, never()).setNewTotalSupply(123L);
	}

	@Test
	void updatesWithChangedRelationshipBalance() {
		// setup:
		var token = new Token(new Id(0, 0, 2));
		token.setType(TokenType.FUNGIBLE_COMMON);
		final var tokenRel = new TokenRelationship(
				token,
				new Account(new Id(0, 0, 3)));
		tokenRel.initBalance(0L);
		tokenRel.setBalance(246L);
		tokenRel.setBalance(123L);

		// when:
		subject.includeChangesToTokenRel(tokenRel);

		// then:
		verify(txnCtx).setTokenTransferLists(List.of(TokenTransferList.newBuilder()
				.setToken(TokenID.newBuilder().setTokenNum(2L))
				.addTransfers(AccountAmount.newBuilder()
						.setAmount(123L)
						.setAccountID(AccountID.newBuilder().setAccountNum(3L))
				).build()));
	}

	@Test
	void doesntUpdatesWithIfNoChangedRelationshipBalance() {
		// setup:
		final var tokenRel = new TokenRelationship(
				new Token(new Id(0, 0, 2)),
				new Account(new Id(0, 0, 3)));
		tokenRel.initBalance(123L);

		// when:
		subject.includeChangesToTokenRel(tokenRel);

		// then:
		verify(txnCtx, never()).setTokenTransferLists(any());
	}
}
