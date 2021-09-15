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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
		subject.includeChangesToTokenRels(List.of(tokenRel));

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
		subject.includeChangesToTokenRels(List.of(tokenRel));

		// then:
		verify(txnCtx, never()).setTokenTransferLists(any());
	}

	@Test
	void updatesReceiptOnUniqueMint() {
		// setup:
		final var supply = 1000;
		final var token = new Token(Id.DEFAULT);
		final var treasury = new Account(Id.DEFAULT);
		final var rel = token.newEnabledRelationship(treasury);
		final var ownershipTracker = mock(OwnershipTracker.class);
		token.setTreasury(treasury);
		token.setSupplyKey(TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKeyUnchecked());
		token.setType(TokenType.NON_FUNGIBLE_UNIQUE);
		token.setTotalSupply(supply);

		token.mint(ownershipTracker, rel, List.of(ByteString.copyFromUtf8("memo")), RichInstant.MISSING_INSTANT);
		subject.includeChangesToToken(token);
		verify(txnCtx).setCreated(List.of(1L));

		var change = mock(OwnershipTracker.Change.class);
		var changedTokenIdMock = mock(Id.class);
		given(changedTokenIdMock.asGrpcToken()).willReturn(IdUtils.asToken("1.2.3"));
		given(change.getNewOwner()).willReturn(Id.DEFAULT);
		given(change.getPreviousOwner()).willReturn(Id.DEFAULT);
		given(change.getSerialNumber()).willReturn(1L);
		given(ownershipTracker.isEmpty()).willReturn(false);
		given(ownershipTracker.getChanges()).willReturn(Map.of(changedTokenIdMock, List.of(change)));
		
		subject.includeOwnershipChanges(ownershipTracker);
		verify(ownershipTracker).getChanges();
		verify(txnCtx).setTokenTransferLists(anyList());
		
		
		for (var id : ownershipTracker.getChanges().keySet()) {
			var changeElement = ownershipTracker.getChanges().get(id);
			verify(id).asGrpcToken();
			for (var ch : changeElement) {
				verify(ch).getNewOwner();
				verify(ch).getPreviousOwner();
			}
		}
	}
	
	@Test
	void updatesReceiptForNewToken() {
		final var treasury = new Account(Id.DEFAULT);
		final var token = Token.fromGrpcOpAndMeta(
				Id.DEFAULT,
				TokenCreateTransactionBody.newBuilder().build(),
				treasury,
				null,
				10L);
		
		subject.includeChangesToToken(token);
		verify(txnCtx).setCreated(Id.DEFAULT.asGrpcToken());
	}

}
