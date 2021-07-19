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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenRelationshipTest {
	private final Id tokenId = new Id(0, 0, 1234);
	private final Id accountId = new Id(1, 0, 1234);
	private final long balance = 1_234L;
	private final JKey kycKey = TxnHandlingScenario.TOKEN_KYC_KT.asJKeyUnchecked();
	private final JKey freezeKey = TxnHandlingScenario.TOKEN_FREEZE_KT.asJKeyUnchecked();

	private Token token;
	private Account account;

	private TokenRelationship subject;

	@BeforeEach
	void setUp() {
		token = new Token(tokenId);
		account = new Account(accountId);
		
		subject = new TokenRelationship(token, account);
		subject.initBalance(balance);
	}

	@Test
	void toStringAsExpected() {
		// given:
		final var desired = "TokenRelationship{notYetPersisted=true, " +
				"account=Account{id=Id{shard=1, realm=0, num=1234}, expiry=0, balance=0, deleted=false, " +
				"tokens=<N/A>}, token=Token{id=Id{shard=0, realm=0, num=1234}, treasury=null, autoRenewAccount=null, " +
				"kycKey=<N/A>, freezeKey=<N/A>, frozenByDefault=false, supplyKey=<N/A>}, balance=1234, " +
				"balanceChange=0, frozen=false, kycGranted=false}";

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
	void givesCorrectRepresentation() {
		subject.getToken().setType(TokenType.NON_FUNGIBLE_UNIQUE);
		assertTrue(subject.hasUniqueRepresentation());


		subject.getToken().setType(TokenType.FUNGIBLE_COMMON);
		assertTrue(subject.hasCommonRepresentation());
	}

	@Test
	void testHashCode() {
		var rel = new TokenRelationship(token, account);
		rel.initBalance(balance);
		assertEquals(rel.hashCode(), subject.hashCode());
	}

	@Test
	void updateFreezeWorksIfFeezeKeyIsPresent() {
		// given:
		subject.setKycGranted(false);
		token.setKycKey(kycKey);

		// when:
		subject.updateKycGranted(true);

		// then:
		assertTrue(subject.isKycGranted());
	}

	@Test
	void updateFreezeFailsAsExpectedIfFreezeKeyIsNotPresent() {
		// given:
		subject.setKycGranted(false);
		token.setKycKey(null);

		// verify
		assertFailsWith(() -> subject.updateKycGranted(true), TOKEN_HAS_NO_KYC_KEY);
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}
