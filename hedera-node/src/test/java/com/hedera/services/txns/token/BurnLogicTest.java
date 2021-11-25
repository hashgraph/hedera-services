package com.hedera.services.txns.token;

import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.*;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BurnLogicTest {
	private final long amount = 123L;
	private final TokenID grpcId = IdUtils.asToken("1.2.3");
	private TokenRelationship treasuryRel;
	private final Id id = new Id(1, 2, 3);
	private final Id treasuryId = new Id(2, 4, 6);
	private final Account treasury = new Account(treasuryId);

	@Mock
	private Token token;

	@Mock
	private TypedTokenStore store;
	@Mock
	private AccountStore accountStore;

	private TransactionBody tokenBurnTxn;

	private BurnLogic subject;

	@BeforeEach
	private void setup() {
		subject = new BurnLogic(store, accountStore);
	}

	@Test
	void followsHappyPathForCommon() {
		// setup:
		treasuryRel = new TokenRelationship(token, treasury);

		givenValidTxnCtx();
		given(store.loadToken(id)).willReturn(token);
		given(token.getTreasury()).willReturn(treasury);
		given(store.loadTokenRelationship(token, treasury)).willReturn(treasuryRel);
		given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
		// when:
		subject.burn(id,amount,anyList());

		// then:
		verify(token).burn(treasuryRel, amount);
		verify(store).commitToken(token);
		verify(store).commitTokenRelationships(List.of(treasuryRel));
	}

	@Test
	void followsHappyPathForUnique() {
		// setup:
		treasuryRel = new TokenRelationship(token, treasury);

		givenValidUniqueTxnCtx();
		given(store.loadToken(id)).willReturn(token);
		given(token.getTreasury()).willReturn(treasury);
		given(store.loadTokenRelationship(token, treasury)).willReturn(treasuryRel);
		given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
		// when:
		subject.burn(id,amount,tokenBurnTxn.getTokenBurn().getSerialNumbersList());

		// then:
		verify(token).getType();
		verify(store).loadUniqueTokens(token, tokenBurnTxn.getTokenBurn().getSerialNumbersList());
		verify(token).burn(any(OwnershipTracker.class), eq(treasuryRel), any(List.class));
		verify(store).commitToken(token);
		verify(store).commitTokenRelationships(List.of(treasuryRel));
		verify(store).commitTrackers(any(OwnershipTracker.class));
		verify(accountStore).commitAccount(any(Account.class));
	}


	private void givenValidTxnCtx() {
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(TokenBurnTransactionBody.newBuilder()
						.setToken(grpcId)
						.setAmount(amount))
				.build();
	}

	private void givenValidUniqueTxnCtx() {
		tokenBurnTxn = TransactionBody.newBuilder()
				.setTokenBurn(TokenBurnTransactionBody.newBuilder()
						.setToken(grpcId)
						.addAllSerialNumbers(List.of(1L)))
				.build();
	}
}