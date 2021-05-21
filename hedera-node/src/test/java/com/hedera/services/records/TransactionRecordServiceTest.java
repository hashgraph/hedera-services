package com.hedera.services.records;

import com.hedera.services.context.TransactionContext;
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
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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
		final var tokenRel = new TokenRelationship(
				new Token(new Id(0, 0, 2)),
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