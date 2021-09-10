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
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.buildAutomaticAssociationMetaData;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountTest {
	private final Id subjectId = new Id(0, 0, 12345);
	private final CopyOnWriteIds assocTokens = new CopyOnWriteIds(new long[] { 666, 0, 0, 777, 0, 0 });
	private final long ownedNfts = 5;
	private final int alreadyUsedAutoAssociations = 123;
	private final int maxAutoAssociations = 1234;
	private final int autoAssociationMetadata = buildAutomaticAssociationMetaData(maxAutoAssociations, alreadyUsedAutoAssociations);

	private final Id accountId = Id.DEFAULT;
	final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	final private long customAutoRenewPeriod = 100_001L;
	final private Long balance = 200L;
	final private String memo = "MEMO";
	final private AccountID proxy = AccountID.newBuilder().setAccountNum(4_321L).build();

	private Account subject;
	private Account recipient;
	private OptionValidator validator;

	@BeforeEach
	void setUp() {
		subject = new Account(subjectId);
		subject.setAssociatedTokens(assocTokens);
		subject.setAutoAssociationMetadata(autoAssociationMetadata);
		subject.setOwnedNfts(ownedNfts);

		validator = mock(ContextOptionValidator.class);
		recipient = mock(Account.class);
	}
	
	@Test
	void transfersHbarsAsExpected() {
		given(recipient.getId()).willReturn(Id.DEFAULT);
		
		subject.setBalance(0);
		assertFailsWith(() -> subject.transferHbar(recipient, 10), INSUFFICIENT_ACCOUNT_BALANCE);
		subject.setBalance(10000);
		var changes = subject.transferHbar(recipient, 500);
		assertNotNull(changes);
		assertEquals(changes.size(), 2);
		assertTrue(changes.get(0).isForHbar());
		assertTrue(changes.get(1).isForHbar());
	}
	
	@Test
	void objectContractWorks() {
		subject.setNew(true);
		assertTrue(subject.isNew());
		final var key = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked(); 
		subject.setKey(key);
		assertEquals(subject.getKey(), key);
		
		subject.setDeleted(true);
		assertTrue(subject.isDeleted());
		
		subject.setReceiverSigRequired(true);
		assertTrue(subject.isReceiverSigRequired());
		
		subject.setSmartContract(true);
		assertTrue(subject.isSmartContract());
		
		assertEquals(0, subject.getExpiry());
	}

	@Test
	void associationTestedAsExpected() {
		assertTrue(subject.isAssociatedWith(new Id(0, 0, 666)));
		assertTrue(subject.isAssociatedWith(new Id(0, 0, 777)));
		assertFalse(subject.isAssociatedWith(new Id(0, 0, 888)));
	}

	@Test
	void toStringAsExpected() {
		// given:
		final var desired = "Account{id=Id{shard=0, realm=0, num=12345}, expiry=0, balance=0, deleted=false, " +
				"tokens=[0.0.666, 0.0.777], ownedNfts=5, alreadyUsedAutoAssociations=123, maxAutoAssociations=1234}";

		// expect:
		assertEquals(desired, subject.toString());
	}

	@Test
	void dissociationHappyPathWorks() {
		// setup:
		final var alreadyAssocTokenId = new Id(0, 0, 666);
		final var dissociationRel = mock(Dissociation.class);
		final var tokenRel = mock(TokenRelationship.class);
		// and:
		final var expectedFinalTokens = "[0.0.777]";

		given(dissociationRel.dissociatingAccountId()).willReturn(subjectId);
		given(dissociationRel.dissociatedTokenId()).willReturn(alreadyAssocTokenId);
		given(dissociationRel.dissociatingAccountRel()).willReturn(tokenRel);
		given(tokenRel.isAutomaticAssociation()).willReturn(true);

		// when:
		subject.dissociateUsing(List.of(dissociationRel), validator);

		// then:
		verify(dissociationRel).updateModelRelsSubjectTo(validator);
		assertEquals(expectedFinalTokens, assocTokens.toReadableIdList());
		assertEquals(alreadyUsedAutoAssociations-1, subject.getAlreadyUsedAutomaticAssociations());
	}

	@Test
	void dissociationFailsInvalidIfRelDoesntReferToUs() {
		// setup:
		final var notOurId = new Id(0, 0, 666);
		final var dissociationRel = mock(Dissociation.class);

		given(dissociationRel.dissociatingAccountId()).willReturn(notOurId);

		// expect:
		assertFailsWith(() -> subject.dissociateUsing(List.of(dissociationRel), validator), FAIL_INVALID);
	}

	@Test
	void failsOnAssociatingWithAlreadyRelatedToken() {
		// setup:
		final var alreadyAssocToken = new Token(new Id(0, 0, 666));

		// expect:
		assertFailsWith(
				() -> subject.associateWith(List.of(alreadyAssocToken), 100, false),
				TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
	}

	@Test
	void cantAssociateWithMoreThanMax() {
		// setup:
		final var firstNewToken = new Token(new Id(0, 0, 888));
		final var secondNewToken = new Token(new Id(0, 0, 999));

		// when:
		assertFailsWith(
				() -> subject.associateWith(List.of(firstNewToken, secondNewToken), 3, false),
				TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);
	}

	@Test
	void canAssociateWithNewToken() {
		// setup:
		final var firstNewToken = new Token(new Id(0, 0, 888));
		final var secondNewToken = new Token(new Id(0, 0, 999));
		final var expectedFinalTokens = "[0.0.666, 0.0.777, 0.0.888, 0.0.999]";
		subject.setAutoAssociationMetadata(autoAssociationMetadata);

		// when:
		subject.associateWith(List.of(firstNewToken, secondNewToken), 10, true);

		// expect:
		assertEquals(expectedFinalTokens, assocTokens.toReadableIdList());
	}

	@Test
	void accountEqualsCheck() {
		// setup:
		var account = new Account(subjectId);
		account.setAssociatedTokens(assocTokens);
		account.setExpiry(1000L);
		account.setBalance(100L);
		account.setOwnedNfts(1L);
		account.incrementOwnedNfts();
		account.setMaxAutomaticAssociations(123);
		account.setAlreadyUsedAutomaticAssociations(12);

		subject.setExpiry(1000L);
		subject.setBalance(100L);
		subject.setOwnedNfts(1L);
		subject.incrementOwnedNfts();
		subject.setAutoAssociationMetadata(account.getAutoAssociationMetadata());

		// when:
		var actualResult = subject.equals(account);

		// expect:
		assertEquals(account.getOwnedNfts(), subject.getOwnedNfts());
		// and:
		assertEquals(account.getId(), subject.getId());
		// and:
		assertEquals(account.getAssociatedTokens(), subject.getAssociatedTokens());
		// and:
		assertEquals(account.getMaxAutomaticAssociations(), subject.getMaxAutomaticAssociations());
		assertEquals(account.getAlreadyUsedAutomaticAssociations(), subject.getAlreadyUsedAutomaticAssociations());
		assertTrue(actualResult);
	}

	@Test
	void accountHashCodeCheck() {
		// setup:
		subject.setOwnedNfts(0);
		var otherSubject = new Account(subjectId);
		otherSubject.incrementOwnedNfts();
		otherSubject.setAssociatedTokens(assocTokens);

		subject.incrementOwnedNfts();
		otherSubject.setAutoAssociationMetadata(autoAssociationMetadata);
		// when:
		var actualResult = subject.hashCode();

		// expect:
		assertEquals(otherSubject.hashCode(), actualResult);
	}

	@Test
	void cannotAutomaticallyAssociateIfLimitReaches() {
		final var firstNewToken = new Token(new Id(0, 0, 888));
		subject.setMaxAutomaticAssociations(maxAutoAssociations);
		subject.setAlreadyUsedAutomaticAssociations(maxAutoAssociations);

		assertFailsWith(
				() -> subject.associateWith(List.of(firstNewToken), 10, true),
				NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
	}

	@Test
	void incrementsTheAlreadyUsedAutoAssociationAsExpected() {
		final var firstNewToken = new Token(new Id(0, 0, 888));
		subject.setMaxAutomaticAssociations(maxAutoAssociations);
		subject.setAlreadyUsedAutomaticAssociations(maxAutoAssociations-1);

		subject.associateWith(List.of(firstNewToken), 10, true);

		assertEquals(maxAutoAssociations, subject.getAlreadyUsedAutomaticAssociations());
	}

	@Test
	void invalidValuesToAlreadyUsedAutoAssociationsFailAsExpected() {
		assertFailsWith(
				() -> subject.setAlreadyUsedAutomaticAssociations(maxAutoAssociations+1),
				NO_REMAINING_AUTOMATIC_ASSOCIATIONS);

		subject.setAlreadyUsedAutomaticAssociations(maxAutoAssociations);

		assertFailsWith(
				() -> subject.incrementUsedAutomaticAssocitions(),
				NO_REMAINING_AUTOMATIC_ASSOCIATIONS);

		subject.setAlreadyUsedAutomaticAssociations(0);

		assertFailsWith(
				() -> subject.decrementUsedAutomaticAssocitions(),
				NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
	}

	@Test
	public void createAccountFromGrpcTransaction() {
		// setup:
		var op =
				CryptoCreateTransactionBody.newBuilder()
					.setMemo(memo)
					.setInitialBalance(balance)
					.setAutoRenewPeriod(Duration.newBuilder().setSeconds(customAutoRenewPeriod))
					.setKey(key)
					.setProxyAccountID(proxy)
					.setMaxAutomaticTokenAssociations(maxAutoAssociations)
					.build();


		Account created = subject.createFromGrpc(accountId, op, Instant.now().getEpochSecond());

		assertEquals(created.getId(), accountId);
		assertTrue(Id.ID_COMPARATOR.compare(created.getProxy(), Id.fromGrpcAccount(proxy)) == 0);
		assertEquals(created.getBalance(), 0);
		assertEquals(created.getMemo(), memo);
		assertEquals(created.getAutoRenewSecs(), customAutoRenewPeriod);
		assertEquals(created.getMaxAutomaticAssociations(), maxAutoAssociations);
	}

	@Test
	public void transferHbar(){
		final var recipient = new Account(Id.DEFAULT);
		subject.setBalance(200L);
		subject.transferHbar(recipient, 100L);
		assertEquals(recipient.getBalance(), 100L);
	}

	@Test
	public void transferHbarWithInsufficientBalance(){
		final var recipient = new Account(Id.DEFAULT);
		subject.setBalance(50L);

		assertFailsWith(() -> subject.transferHbar(recipient, 100L), INSUFFICIENT_ACCOUNT_BALANCE);
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}
