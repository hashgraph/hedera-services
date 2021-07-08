package com.hedera.services.store.models;

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
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AccountTest {
	private Id subjectId = new Id(0, 0, 12345);
	private Id treasuryId = new Id(0, 0, 123456);
	private CopyOnWriteIds assocTokens = new CopyOnWriteIds(new long[] { 666, 0, 0, 777, 0, 0 });

	private Account subject;
	private Account treasuryAccount;
	private OptionValidator optionValidator;

	@BeforeEach
	void setUp() {
		subject = new Account(subjectId);
		treasuryAccount = new Account(treasuryId);
		subject.setAssociatedTokens(assocTokens);

		optionValidator = mock(ContextOptionValidator.class);
	}

	@Test
	void toStringAsExpected() {
		// given:
		final var desired = "Account{id=Id{shard=0, realm=0, num=12345}, expiry=0, balance=0, deleted=false, " +
				"tokens=[0.0.666, 0.0.777]}";

		// expect:
		assertEquals(desired, subject.toString());
	}

	@Test
	void failsOnAssociatingWithAlreadyRelatedToken() {
		// setup:
		final var alreadyAssocToken = new Token(new Id(0, 0, 666));

		// expect:
		assertFailsWith(
				() -> subject.associateWith(List.of(alreadyAssocToken), 100),
				TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
	}

	@Test
	void cantAssociateWithMoreThanMax() {
		// setup:
		final var firstNewToken = new Token(new Id(0, 0, 888));
		final var secondNewToken = new Token(new Id(0, 0, 999));

		// when:
		assertFailsWith(
				() -> subject.associateWith(List.of(firstNewToken, secondNewToken), 3),
				TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);
	}

	@Test
	void canAssociateWithNewToken() {
		// setup:
		final var firstNewToken = new Token(new Id(0, 0, 888));
		final var secondNewToken = new Token(new Id(0, 0, 999));
		final var expectedFinalTokens = "[0.0.666, 0.0.777, 0.0.888, 0.0.999]";

		// when:
		subject.associateWith(List.of(firstNewToken, secondNewToken), 10);

		// expect:
		assertEquals(expectedFinalTokens, assocTokens.toReadableIdList());
	}

	@Test
	void accountEqualsCheck() {
		// setup:
		var account = new Account(subjectId);
		account.setAssociatedTokens(assocTokens);
		account.setExpiry(1000L);
		account.initBalance(100L);
		account.setOwnedNfts(1L);
		account.incrementOwnedNfts();

		subject.setExpiry(1000L);
		subject.initBalance(100L);
		subject.setOwnedNfts(1L);
		subject.incrementOwnedNfts();

		// when:
		var actualResult = subject.equals(account);

		// expect:
		assertEquals(account.getOwnedNfts(), subject.getOwnedNfts());
		// and:
		assertEquals(account.getId(), subject.getId());
		// and:
		assertEquals(account.getAssociatedTokens(), subject.getAssociatedTokens());
		// and:
		assertTrue(actualResult);
	}

	@Test
	void accountHashCodeCheck() {
		// setup:
		var otherSubject = new Account(subjectId);
		otherSubject.incrementOwnedNfts();
		otherSubject.setAssociatedTokens(assocTokens);

		subject.incrementOwnedNfts();

		// when:
		var actualResult = subject.hashCode();

		// expect:
		assertEquals(otherSubject.hashCode(), actualResult);
	}

	@Test
	void dissociationWorks() {
		// setup:
		final var dissociatingToken = new Token(new Id(0,0,777));
		final var expectedFinalTokens = "[0.0.666]";
		final var dissocRel = new TokenRelationship(dissociatingToken, subject);
		final var treasuryRel = new TokenRelationship(dissociatingToken, treasuryAccount);
		dissociatingToken.setType(TokenType.FUNGIBLE_COMMON);
		given(optionValidator.isValidExpiry(any())).willReturn(true);

		// when:
		subject.dissociateWith(List.of(Pair.of(dissocRel, treasuryRel)), optionValidator);

		// expect:
		assertEquals(expectedFinalTokens, assocTokens.toReadableIdList());
	}

	@Test
	void dissociationOnUniqueWorks() {
		// setup:
		final var dissociatingToken = new Token(new Id(0,0,777));
		final var expectedFinalTokens = "[0.0.666]";
		final var dissocRel = new TokenRelationship(dissociatingToken, subject);
		final var treasuryRel = new TokenRelationship(dissociatingToken, treasuryAccount);
		dissociatingToken.setType(TokenType.NON_FUNGIBLE_UNIQUE);
		given(optionValidator.isValidExpiry(any())).willReturn(true);

		// when:
		subject.dissociateWith(List.of(Pair.of(dissocRel, treasuryRel)), optionValidator);

		// expect:
		assertEquals(expectedFinalTokens, assocTokens.toReadableIdList());
	}

	@Test
	void failsOnDissociatingWithNonAssociatedToken() {
		// setup:
		final var dissociatingToken = new Token(new Id(0,0,786));
		final var dissocRel = new TokenRelationship(dissociatingToken, new Account(Id.DEFAULT));
		final var treasuryRel = new TokenRelationship(dissociatingToken, treasuryAccount);
		dissociatingToken.setType(TokenType.FUNGIBLE_COMMON);
		// expect:
		assertFailsWith(
				() -> subject.dissociateWith(List.of(Pair.of(dissocRel, treasuryRel)), optionValidator),
				TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
	}

	@Test
	void failOnDissociatingWithNonZeroOwnedNfts() {
		// setup:
		final var dissocToken = new Token(new Id(0, 0, 787));
		final var dissocRel = new TokenRelationship(dissocToken, subject);
		final var treasuryRel = new TokenRelationship(dissocToken, treasuryAccount);
		dissocToken.setType(TokenType.NON_FUNGIBLE_UNIQUE);
		dissocRel.setBalance(5);
		assocTokens.addAllIds(Set.of(dissocToken.getId()));

		// then:
		assertFailsWith(
				() -> subject.dissociateWith(List.of(Pair.of(dissocRel, treasuryRel)), optionValidator),
				TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES
		);
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}
