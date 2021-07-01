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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TokenTest {
	private final JKey someKey = TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKeyUnchecked();
	private final long initialSupply = 1_000L;
	private final long initialTreasuryBalance = 500L;
	private final Id id = new Id(1,2 , 3);
	private final Id treasuryId = new Id(2,2 , 3);
	private final Id nonTreasuryId = new Id(3,2 , 3);
	private final Account treasuryAccount = new Account(treasuryId);
	private final Account nonTreasuryAccount = new Account(nonTreasuryId);

	private Token subject;
	private TokenRelationship treasuryRel;
	private TokenRelationship nonTreasuryRel;

	@BeforeEach
	void setUp() {
		subject = new Token(id);
		subject.initTotalSupply(initialSupply);
		subject.setTreasury(treasuryAccount);

		treasuryRel = new TokenRelationship(subject, treasuryAccount);
		treasuryRel.initBalance(initialTreasuryBalance);
		nonTreasuryRel = new TokenRelationship(subject, nonTreasuryAccount);
	}

	@Test
	void constructsExpectedDefaultRelWithNoKeys() {
		// setup:
		nonTreasuryRel.setKycGranted(true);

		// when:
		final var newRel = subject.newRelationshipWith(nonTreasuryAccount);

		// then:
		assertEquals(newRel, nonTreasuryRel);
	}

	@Test
	void constructsExpectedDefaultRelWithFreezeKeyAndFrozenByDefault() {
		// setup:
		nonTreasuryRel.setFrozen(true);
		nonTreasuryRel.setKycGranted(true);

		// given:
		subject.setFreezeKey(someKey);
		subject.setFrozenByDefault(true);

		// when:
		final var newRel = subject.newRelationshipWith(nonTreasuryAccount);

		// then:
		assertEquals(newRel, nonTreasuryRel);
	}

	@Test
	void constructsExpectedDefaultRelWithFreezeKeyAndNotFrozenByDefault() {
		// setup:
		nonTreasuryRel.setKycGranted(true);

		// given:
		subject.setFreezeKey(someKey);
		subject.setFrozenByDefault(false);

		// when:
		final var newRel = subject.newRelationshipWith(nonTreasuryAccount);

		// then:
		assertEquals(newRel, nonTreasuryRel);
	}

	@Test
	void constructsExpectedDefaultRelWithKycKeyOnly() {
		// given:
		subject.setKycKey(someKey);

		// when:
		final var newRel = subject.newRelationshipWith(nonTreasuryAccount);

		// then:
		assertEquals(newRel, nonTreasuryRel);
	}

	@Test
	void failsInvalidIfLogicImplTriesToChangeNonTreasurySupply() {
		assertFailsWith(() -> subject.burn(nonTreasuryRel, 1L), FAIL_INVALID);
		assertFailsWith(() -> subject.mint(nonTreasuryRel, 1L), FAIL_INVALID);
	}

	@Test
	void cantBurnOrMintNegativeAmounts() {
		assertFailsWith(() -> subject.burn(treasuryRel, -1L), FAIL_INVALID);
		assertFailsWith(() -> subject.mint(treasuryRel, -1L), FAIL_INVALID);
	}

	@Test
	void cantBurnOrMintWithoutSupplyKey() {
		subject.setSupplyKey(null);
		subject.setType(TokenType.FUNGIBLE_COMMON);
		assertFailsWith(() -> subject.burn(treasuryRel, 1L), TOKEN_HAS_NO_SUPPLY_KEY);
		assertFailsWith(() -> subject.mint(treasuryRel, 1L), TOKEN_HAS_NO_SUPPLY_KEY);
	}

	@Test
	void cannotChangeTreasuryBalanceToNegative() {
		// given:
		subject.setSupplyKey(someKey);
		subject.setType(TokenType.FUNGIBLE_COMMON);
		subject.initSupplyConstraints(TokenSupplyType.FINITE, 10000);

		assertFailsWith(() -> subject.burn(treasuryRel, initialTreasuryBalance + 1), INSUFFICIENT_TOKEN_BALANCE);
	}

	@Test
	void cannotChangeSupplyToNegative() {
		// setup:
		final long overflowMint = Long.MAX_VALUE - initialSupply + 1L;

		// given:
		subject.setSupplyKey(someKey);
		subject.setType(TokenType.FUNGIBLE_COMMON);

		assertFailsWith(() -> subject.mint(treasuryRel, overflowMint), INVALID_TOKEN_MINT_AMOUNT);
		assertFailsWith(() -> subject.burn(treasuryRel, initialSupply + 1), INVALID_TOKEN_BURN_AMOUNT);
	}

	@Test
	void burnsAsExpected() {
		subject.setType(TokenType.FUNGIBLE_COMMON);
		subject.initSupplyConstraints(TokenSupplyType.FINITE, 20000L);
		final long burnAmount = 100L;

		// given:
		subject.setSupplyKey(someKey);

		// when:
		subject.burn(treasuryRel, burnAmount);

		// then:
		assertEquals(initialSupply - burnAmount, subject.getTotalSupply());
		assertEquals(-burnAmount, treasuryRel.getBalanceChange());
		assertEquals(initialTreasuryBalance - burnAmount, treasuryRel.getBalance());
	}

	@Test
	void burnsUniqueAsExpected(){
		subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
		subject.initSupplyConstraints(TokenSupplyType.FINITE, 20000L);
		subject.setSupplyKey(someKey);

		var ownershipTracker = mock(OwnershipTracker.class);
		subject.burn(ownershipTracker, treasuryRel, List.of(1L));
		assertEquals(initialSupply - 1, subject.getTotalSupply());
		assertEquals(-1, treasuryRel.getBalanceChange());
		verify(ownershipTracker).add(eq(subject.getId()), any());
		assertEquals(true, subject.hasBurnedUniqueTokens());
		assertEquals(1, subject.burnedUniqueTokens().get(0).getSerialNumber());
	}

	@Test
	void mintsUniqueAsExpected(){
		subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
		subject.initSupplyConstraints(TokenSupplyType.FINITE, 20000L);
		subject.setSupplyKey(someKey);

		var ownershipTracker = mock(OwnershipTracker.class);
		subject.mint(ownershipTracker, treasuryRel, List.of(ByteString.copyFromUtf8("memo")), RichInstant.fromJava(Instant.now()));
		assertEquals(initialSupply + 1, subject.getTotalSupply());
		assertEquals(1, treasuryRel.getBalanceChange());
		verify(ownershipTracker).add(eq(subject.getId()), Mockito.any());
		assertTrue(subject.hasMintedUniqueTokens());
		assertEquals(1, subject.mintedUniqueTokens().get(0).getSerialNumber());
	}

	@Test
	void mintsAsExpected() {
		final long mintAmount = 100L;
		subject.setType(TokenType.FUNGIBLE_COMMON);
		subject.initSupplyConstraints(TokenSupplyType.FINITE, 100000);
		// given:
		subject.setSupplyKey(someKey);

		// when:
		subject.mint(treasuryRel, mintAmount);

		// then:
		assertEquals(initialSupply + mintAmount, subject.getTotalSupply());
		assertEquals(+mintAmount, treasuryRel.getBalanceChange());
		assertEquals(initialTreasuryBalance + mintAmount, treasuryRel.getBalance());
	}

	@Test
	void reflectionObjectHelpersWork() {
		final var otherToken = new Token(new Id(1,2, 3));

		assertNotEquals(subject, otherToken);
		assertNotEquals(subject.hashCode(), otherToken.hashCode());
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}
