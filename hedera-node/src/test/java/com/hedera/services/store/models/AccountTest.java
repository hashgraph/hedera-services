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

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.state.merkle.internals.BitPackUtils.buildAutomaticAssociationMetaData;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.swirlds.common.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountTest {
	private static final byte[] mockCreate2Addr = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
	private final Id subjectId = new Id(0, 0, 12345);
	private final CopyOnWriteIds assocTokens = new CopyOnWriteIds(new long[] { 666, 0, 0, 777, 0, 0 });
	private final long ownedNfts = 5;
	private final int alreadyUsedAutoAssociations = 123;
	private final int maxAutoAssociations = 1234;
	private final int autoAssociationMetadata = buildAutomaticAssociationMetaData(maxAutoAssociations,
			alreadyUsedAutoAssociations);

	private Account subject;
	private OptionValidator validator;

	@BeforeEach
	void setUp() {
		subject = new Account(subjectId);
		subject.setAssociatedTokens(assocTokens);
		subject.setAutoAssociationMetadata(autoAssociationMetadata);
		subject.setOwnedNfts(ownedNfts);

		validator = mock(ContextOptionValidator.class);
	}

	@Test
	void canonicalAddressIsMirrorWithEmptyAlias() {
		assertEquals(EntityNum.fromModel(subjectId).toEvmAddress(), subject.canonicalAddress());
	}

	@Test
	void canonicalAddressIs20ByteAliasIfPresent() {
		subject.setAlias(ByteString.copyFrom(mockCreate2Addr));
		assertEquals(Address.wrap(Bytes.wrap(mockCreate2Addr)), subject.canonicalAddress());
	}

	@Test
	void objectContractWorks() {
		final var TEST_KEY = TOKEN_ADMIN_KT.asJKeyUnchecked();
		final var TEST_LONG_VALUE = 1L;
		final var TEST_BOOLEAN_VALUE = true;

		subject.setExpiry(TEST_LONG_VALUE);
		assertEquals(TEST_LONG_VALUE, subject.getExpiry());

		subject.setDeleted(TEST_BOOLEAN_VALUE);
		assertTrue(subject.isDeleted());

		subject.setSmartContract(TEST_BOOLEAN_VALUE);
		assertTrue(subject.isSmartContract());

		subject.setReceiverSigRequired(TEST_BOOLEAN_VALUE);
		assertTrue(subject.isReceiverSigRequired());

		subject.initBalance(TEST_LONG_VALUE);
		assertEquals(TEST_LONG_VALUE, subject.getBalance());

		subject.setAutoRenewSecs(TEST_LONG_VALUE);
		assertEquals(TEST_LONG_VALUE, subject.getAutoRenewSecs());

		subject.setKey(TEST_KEY);
		assertEquals(TEST_KEY, subject.getKey());

		subject.setMemo("Test memo");
		assertEquals("Test memo", subject.getMemo());

		subject.setProxy(Id.DEFAULT);
		assertEquals(Id.DEFAULT, subject.getProxy());
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
		final var desired = "Account{id=Id[shard=0, realm=0, num=12345], expiry=0, balance=0, deleted=false, " +
				"tokens=[0" +
				".0.666, 0.0.777], ownedNfts=5, alreadyUsedAutoAssociations=123, maxAutoAssociations=1234, " +
				"alias=" + subject.getAlias().toStringUtf8() + "}";

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
		assertEquals(alreadyUsedAutoAssociations - 1, subject.getAlreadyUsedAutomaticAssociations());
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
		account.initBalance(100L);
		account.setOwnedNfts(1L);
		account.incrementOwnedNfts();
		account.setMaxAutomaticAssociations(123);
		account.setAlreadyUsedAutomaticAssociations(12);
		account.setSmartContract(false);

		subject.setExpiry(1000L);
		subject.initBalance(100L);
		subject.setOwnedNfts(1L);
		subject.incrementOwnedNfts();
		subject.setAutoAssociationMetadata(account.getAutoAssociationMetadata());
		subject.setSmartContract(false);

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
		assertEquals(subject.isSmartContract(), account.isSmartContract());
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
		subject.setAlreadyUsedAutomaticAssociations(maxAutoAssociations - 1);

		subject.associateWith(List.of(firstNewToken), 10, true);

		assertEquals(maxAutoAssociations, subject.getAlreadyUsedAutomaticAssociations());
	}

	@Test
	void invalidValuesToAlreadyUsedAutoAssociationsFailAsExpected() {
		assertFailsWith(
				() -> subject.setAlreadyUsedAutomaticAssociations(maxAutoAssociations + 1),
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

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}
