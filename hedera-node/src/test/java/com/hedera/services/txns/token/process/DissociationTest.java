package com.hedera.services.txns.token.process;

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

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.state.enums.TokenType.FUNGIBLE_COMMON;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DissociationTest {
	private final long tokenExpiry = 1_234_567L;
	private final Id accountId = new Id(1, 2, 3);
	private final Id tokenId = new Id(2, 3, 4);
	private final Id treasuryId = new Id(3, 4, 5);
	private final Id veryAncientTreasuryId = new Id(0, 0, 3);
	private final Token token = new Token(tokenId);
	private final Account account = new Account(accountId);
	private final Account treasury = new Account(treasuryId);
	private final Account ancientTreasury = new Account(veryAncientTreasuryId);
	private TokenRelationship dissociatingAccountRel = new TokenRelationship(token, account);
	private TokenRelationship dissociatedTokenTreasuryRel = new TokenRelationship(token, treasury);
	private TokenRelationship ancientTokenTreasuryRel = new TokenRelationship(token, ancientTreasury);

	{
		token.setTreasury(treasury);
		token.setExpiry(tokenExpiry);
		token.setType(FUNGIBLE_COMMON);
		dissociatingAccountRel.markAsPersisted();
	}

	@Mock
	private TypedTokenStore tokenStore;
	@Mock
	private OptionValidator validator;

	@Test
	void loadsExpectedRelsForExtantToken() {
		given(tokenStore.loadPossiblyDeletedOrAutoRemovedToken(tokenId)).willReturn(token);
		given(tokenStore.loadTokenRelationship(token, account)).willReturn(dissociatingAccountRel);
		given(tokenStore.loadTokenRelationship(token, treasury)).willReturn(dissociatedTokenTreasuryRel);

		// when:
		final var subject = Dissociation.loadFrom(tokenStore, account, tokenId);

		// then:
		assertSame(dissociatingAccountRel, subject.dissociatingAccountRel());
		assertSame(dissociatedTokenTreasuryRel, subject.dissociatedTokenTreasuryRel());
		assertSame(tokenId, subject.dissociatedTokenId());
		assertSame(accountId, subject.dissociatingAccountId());
	}

	@Test
	void loadsExpectedRelsForAutoRemovedToken() {
		// setup:
		token.markAutoRemoved();

		given(tokenStore.loadPossiblyDeletedOrAutoRemovedToken(tokenId)).willReturn(token);
		given(tokenStore.loadTokenRelationship(token, account)).willReturn(dissociatingAccountRel);

		// when:
		final var subject = Dissociation.loadFrom(tokenStore, account, tokenId);

		// then:
		verify(tokenStore, never()).loadTokenRelationship(token, treasury);
		// and:
		assertSame(dissociatingAccountRel, subject.dissociatingAccountRel());
		assertNull(subject.dissociatedTokenTreasuryRel());
		assertSame(tokenId, subject.dissociatedTokenId());
		assertSame(accountId, subject.dissociatingAccountId());
	}

	@Test
	void requiresUpdateDoneBeforeRevealingRels() {
		// given:
		final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.addUpdatedModelRelsTo(new ArrayList<>()));
	}

	@Test
	void processesAutoRemovedTokenAsExpected() {
		// setup:
		final var subject = new Dissociation(dissociatingAccountRel, null);
		final List<TokenRelationship> changed = new ArrayList<>();

		// when:
		subject.updateModelRelsSubjectTo(validator);
		// and:
		subject.addUpdatedModelRelsTo(changed);

		// then:
		assertEquals(1, changed.size());
		assertSame(dissociatingAccountRel, changed.get(0));
		assertTrue(dissociatingAccountRel.isDestroyed());
	}

	@Test
	void rejectsDissociatingTokenTreasury() {
		// setup:
		token.setTreasury(account);

		// given:
		final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

		// expect:
		assertFailsWith(() -> subject.updateModelRelsSubjectTo(validator), ACCOUNT_IS_TREASURY);
	}

	@Test
	void rejectsDissociatingFrozenAccount() {
		// setup:
		dissociatingAccountRel.setFrozen(true);

		// given:
		final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

		// expect:
		assertFailsWith(() -> subject.updateModelRelsSubjectTo(validator), ACCOUNT_FROZEN_FOR_TOKEN);
	}

	@Test
	void normalCaseOnlyUpdatesDissociatingRel() {
		// given:
		final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);
		// and:
		final List<TokenRelationship> accum = new ArrayList<>();

		// when:
		subject.updateModelRelsSubjectTo(validator);
		// and:
		subject.addUpdatedModelRelsTo(accum);

		// then:
		assertEquals(accum.size(), 1);
		assertSame(dissociatingAccountRel, accum.get(0));
		assertTrue(dissociatingAccountRel.isDestroyed());
	}

	@Test
	void requiresZeroBalanceWhenDissociatingFromActiveToken() {
		// setup:
		final long balance = 1_234;
		dissociatingAccountRel.initBalance(balance);

		given(validator.isAfterConsensusSecond(tokenExpiry)).willReturn(true);
		// and:
		final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

		assertFailsWith(() -> subject.updateModelRelsSubjectTo(validator), TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
	}

	@Test
	void cannotAutoRevertOwnershipToTreasuryEvenForExpired() {
		// setup:
		final long balance = 1;
		dissociatingAccountRel.initBalance(balance);
		token.setType(NON_FUNGIBLE_UNIQUE);
		// and:
		final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

		// expect:
		assertFailsWith(() -> subject.updateModelRelsSubjectTo(validator), ACCOUNT_STILL_OWNS_NFTS);
	}

	@Test
	void autoTransfersBalanceBackToTreasuryForExpiredToken() {
		// setup:
		final long balance = 1_234;
		dissociatingAccountRel.initBalance(balance);
		dissociatedTokenTreasuryRel.initBalance(balance);
		// and:
		final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);
		// and:
		final List<TokenRelationship> accum = new ArrayList<>();

		// when:
		subject.updateModelRelsSubjectTo(validator);
		// and:
		subject.addUpdatedModelRelsTo(accum);

		// then:
		assertEquals(accum.size(), 2);
		assertEquals(dissociatingAccountRel.getBalanceChange(), -balance);
		assertSame(dissociatingAccountRel, accum.get(0));
		assertTrue(dissociatingAccountRel.isDestroyed());
		// and:
		assertSame(dissociatedTokenTreasuryRel, accum.get(1));
		assertEquals(dissociatedTokenTreasuryRel.getBalanceChange(), +balance);
	}

	@Test
	void autoTransfersBalanceBackToTreasuryRespectingIdOrdering() {
		// setup:
		final long balance = 1_234;
		dissociatingAccountRel.initBalance(balance);
		ancientTokenTreasuryRel.initBalance(balance);
		// and:
		final var subject = new Dissociation(dissociatingAccountRel, ancientTokenTreasuryRel);
		// and:
		final List<TokenRelationship> accum = new ArrayList<>();

		// when:
		subject.updateModelRelsSubjectTo(validator);
		// and:
		subject.addUpdatedModelRelsTo(accum);

		// then:
		assertEquals(accum.size(), 2);
		assertEquals(dissociatingAccountRel.getBalanceChange(), -balance);
		assertSame(dissociatingAccountRel, accum.get(1));
		assertTrue(dissociatingAccountRel.isDestroyed());
		// and:
		assertSame(ancientTokenTreasuryRel, accum.get(0));
		assertEquals(ancientTokenTreasuryRel.getBalanceChange(), +balance);
	}

	@Test
	void oksDissociatedDeletedTokenTreasury() {
		// setup:
		token.setTreasury(account);
		token.setIsDeleted(true);

		// given:
		final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

		// expect:
		assertDoesNotThrow(() -> subject.updateModelRelsSubjectTo(validator));
	}

	@Test
	void toStringWorks() {
		// given:
		final var desired = "Dissociation{dissociatingAccountId=Id{shard=1, realm=2, num=3}, " +
				"dissociatedTokenId=Id{shard=2, realm=3, num=4}, dissociatedTokenTreasuryId=Id{shard=3, realm=4, num=5}, " +
				"expiredTokenTreasuryReceivedBalance=false}";

		// when:
		final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

		// expect:
		assertEquals(desired, subject.toString());
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}