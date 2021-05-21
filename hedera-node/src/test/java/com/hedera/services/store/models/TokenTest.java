package com.hedera.services.store.models;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static org.junit.jupiter.api.Assertions.*;

class TokenTest {
	private final JKey supplyKey = TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKeyUnchecked();
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
		assertFailsWith(() -> subject.burn(treasuryRel, 1L), TOKEN_HAS_NO_SUPPLY_KEY);
		assertFailsWith(() -> subject.mint(treasuryRel, 1L), TOKEN_HAS_NO_SUPPLY_KEY);
	}

	@Test
	void cannotChangeTreasuryBalanceToNegative() {
		// setup:
		final long overflowMint = Long.MAX_VALUE - initialTreasuryBalance + 1L;

		// given:
		subject.setSupplyKey(supplyKey);

		assertFailsWith(() -> subject.burn(treasuryRel, initialTreasuryBalance + 1), INSUFFICIENT_TOKEN_BALANCE);
		assertFailsWith(() -> subject.mint(treasuryRel, overflowMint), INSUFFICIENT_TOKEN_BALANCE);
	}

	@Test
	void cannotChangeSupplyToNegative() {
		// setup:
		final long overflowMint = Long.MAX_VALUE - initialSupply + 1L;

		// given:
		subject.setSupplyKey(supplyKey);

		assertFailsWith(() -> subject.mint(treasuryRel, overflowMint), INVALID_TOKEN_MINT_AMOUNT);
		assertFailsWith(() -> subject.burn(treasuryRel, initialSupply + 1), INVALID_TOKEN_BURN_AMOUNT);
	}

	@Test
	void burnsAsExpected() {
		final long burnAmount = 100L;

		// given:
		subject.setSupplyKey(supplyKey);

		// when:
		subject.burn(treasuryRel, burnAmount);

		// then:
		assertEquals(initialSupply - burnAmount, subject.getTotalSupply());
		assertEquals(-burnAmount, treasuryRel.getBalanceChange());
		assertEquals(initialTreasuryBalance - burnAmount, treasuryRel.getBalance());
	}

	@Test
	void mintsAsExpected() {
		final long mintAmount = 100L;

		// given:
		subject.setSupplyKey(supplyKey);

		// when:
		subject.mint(treasuryRel, mintAmount);

		// then:
		assertEquals(initialSupply + mintAmount, subject.getTotalSupply());
		assertEquals(+mintAmount, treasuryRel.getBalanceChange());
		assertEquals(initialTreasuryBalance + mintAmount, treasuryRel.getBalance());
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}
}