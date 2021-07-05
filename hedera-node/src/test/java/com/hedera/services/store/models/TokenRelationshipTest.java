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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenRelationshipTest {
	private final Id tokenId = new Id(0, 0, 1234);
	private final Id accountId = new Id(1, 0, 1234);
	private final Id treasuryId = new Id(1, 0, 4321);
	private final long balance = 1_234L;
	private final JKey kycKey = TxnHandlingScenario.TOKEN_KYC_KT.asJKeyUnchecked();
	private final JKey freezeKey = TxnHandlingScenario.TOKEN_FREEZE_KT.asJKeyUnchecked();

	private Token token;
	private Account account;
	private OptionValidator validator;

	private TokenRelationship subject;
	TokenRelationship treasuryRealtionship;

	@BeforeEach
	void setUp() {
		token = new Token(tokenId);
		account = new Account(accountId);
		Account treasury = new Account(treasuryId);
		validator = mock(ContextOptionValidator.class);

		subject = new TokenRelationship(token, account);
		treasuryRealtionship = new TokenRelationship(token, treasury);
		token.setTreasury(treasury);
		subject.initBalance(balance);
		treasuryRealtionship.setBalance(balance);
	}

	@Test
	void toStringAsExpected() {
		// given:
		final var desired = "TokenRelationship{notYetPersisted=true, " +
				"account=Account{id=Id{shard=1, realm=0, num=1234}, expiry=0, balance=0, deleted=false, " +
				"tokens=<N/A>}, token=Token{id=Id{shard=0, realm=0, num=1234}, " +
				"treasury=Account{id=Id{shard=1, realm=0, num=4321}, expiry=0, balance=0, deleted=false, tokens=<N/A>}," +
				" autoRenewAccount=null, kycKey=<N/A>, freezeKey=<N/A>, frozenByDefault=false, supplyKey=<N/A>}," +
				" balance=1234, balanceChange=0, frozen=false, kycGranted=false}";

		// expect:
		assertEquals(desired, subject.toString());
	}

	@Test
	void cannotChangeBalanceIfFrozenForToken() {
		// given:
		token.setFreezeKey(freezeKey);
		subject.setFrozen(true);

		assertFailsWith(() -> subject.setBalance(balance + 1), ACCOUNT_FROZEN_FOR_TOKEN);
	}

	@Test
	void canChangeBalanceIfUnfrozenForToken() {
		// given:
		token.setFreezeKey(freezeKey);
		subject.setFrozen(false);

		// when:
		subject.setBalance(balance + 1);

		// then:
		assertEquals(1, subject.getBalanceChange());
	}

	@Test
	void canChangeBalanceIfNoFreezeKey() {
		// given:
		subject.setFrozen(true);

		// when:
		subject.setBalance(balance + 1);

		// then:
		assertEquals(1, subject.getBalanceChange());
	}

	@Test
	void cannotChangeBalanceIfKycNotGranted() {
		// given:
		token.setKycKey(kycKey);
		subject.setKycGranted(false);

		assertFailsWith(() -> subject.setBalance(balance + 1), ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN);
	}

	@Test
	void canChangeBalanceIfKycGranted() {
		// given:
		token.setKycKey(kycKey);
		subject.setKycGranted(true);

		// when:
		subject.setBalance(balance + 1);

		// then:
		assertEquals(1, subject.getBalanceChange());
	}

	@Test
	void canChangeBalanceIfNoKycKey() {
		// given:
		subject.setKycGranted(false);

		// when:
		subject.setBalance(balance + 1);

		// then:
		assertEquals(1, subject.getBalanceChange());
	}

	@Test
	void dissociateWithTreasuryFails() {
		assertFailsWith(() -> treasuryRealtionship.validateAndDissociate(validator, treasuryRealtionship), ACCOUNT_IS_TREASURY);
	}

	@Test
	void dissociateWithFrozenRelationshipFails() {
		// given:
		subject.setFrozen(true);

		// verify:
		assertFailsWith(() -> subject.validateAndDissociate(validator, treasuryRealtionship), ACCOUNT_FROZEN_FOR_TOKEN);
		assertEquals(balance, subject.getBalance());
		assertEquals(balance, treasuryRealtionship.getBalance());
	}

	@Test
	void dissociateWithNonZeroBalanceFails() {
		// given:
		given(validator.isValidExpiry(any())).willReturn(true);

		// verify:
		assertFailsWith(() -> subject.validateAndDissociate(validator, treasuryRealtionship), TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
		assertEquals(balance, subject.getBalance());
		assertEquals(balance, treasuryRealtionship.getBalance());
	}

	@Test
	void dissociationTransfersBalanceAsExpectedToTreasuryOfExpiredToken() {
		// given:
		given(validator.isValidExpiry(any())).willReturn(false);

		// when:
		subject.validateAndDissociate(validator, treasuryRealtionship);

		// then:
		assertEquals(0, subject.getBalance());
		assertEquals(2*balance, treasuryRealtionship.getBalance());
	}

	@Test
	void givesCorrectRepresentation(){
		subject.getToken().setType(TokenType.NON_FUNGIBLE_UNIQUE);
		assertTrue(subject.hasUniqueRepresentation());


		subject.getToken().setType(TokenType.FUNGIBLE_COMMON);
		assertTrue(subject.hasCommonRepresentation());
	}

	@Test
	void testHashCode(){
		var rel = new TokenRelationship(token, account);
		rel.initBalance(balance);
		assertEquals(rel.hashCode(), subject.hashCode());
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}
