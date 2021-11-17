package com.hedera.services.context;

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

import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SideEffectsTrackerTest {
	private SideEffectsTracker subject;

	@BeforeEach
	void setUp() {
		subject = new SideEffectsTracker();
	}

	@Test
	void tracksAndResetsTokenSupplyAsExpected() {
		final var newSupply = 1_234L;

		subject.trackTokenSupply(newSupply);

		assertTrue(subject.hasTrackedTokenSupply());
		assertEquals(newSupply, subject.getTrackedTokenSupply());

		subject.reset();
		assertFalse(subject.hasTrackedTokenSupply());
	}

	@Test
	void tracksAndResetsNftMintsAsExpected() {
		subject.trackMintedNft(1L);
		subject.trackMintedNft(2L);
		subject.trackMintedNft(3L);

		assertTrue(subject.hasTrackedNftMints());
		assertEquals(List.of(1L, 2L, 3L), subject.getTrackedNftMints());

		subject.reset();

		assertFalse(subject.hasTrackedNftMints());
		assertTrue(subject.getTrackedNftMints().isEmpty());
	}

	@Test
	void tracksAndResetsAutoAssociationsAsExpected() {
		final var expected = List.of(
				new FcTokenAssociation(aToken.getTokenNum(), aAccount.getAccountNum()),
				new FcTokenAssociation(bToken.getTokenNum(), bAccount.getAccountNum()));

		subject.trackAutoAssociation(aToken, aAccount);
		subject.trackAutoAssociation(bToken, bAccount);

		assertEquals(expected, subject.getTrackedAutoAssociations());

		subject.reset();

		assertTrue(subject.getTrackedAutoAssociations().isEmpty());
	}

	@Test
	void canClearJustTokenChanges() {
		subject.trackHbarChange(aAccount, aFirstBalanceChange);
		subject.trackTokenUnitsChange(bToken, cAccount, cOnlyBalanceChange);
		subject.trackNftOwnerChange(cSN1, aAccount, bAccount);
		subject.trackAutoAssociation(aToken, bAccount);
		subject.trackMintedNft(1L);
		subject.trackTokenSupply(1_234L);

		subject.resetTrackedTokenChanges();

		assertFalse(subject.hasTrackedTokenSupply());
		assertFalse(subject.hasTrackedNftMints());
		assertTrue(subject.getTrackedAutoAssociations().isEmpty());
		assertSame(Collections.emptyList(), subject.getNetTrackedTokenUnitAndOwnershipChanges());
		final var netChanges = subject.getNetTrackedHbarChanges();
		assertEquals(1, netChanges.getAccountAmountsCount());
		final var aChange = netChanges.getAccountAmounts(0);
		assertEquals(aAccount, aChange.getAccountID());
		assertEquals(aFirstBalanceChange, aChange.getAmount());
	}

	@Test
	void tracksAndResetsTokenUnitAndOwnershipChangesAsExpected() {
		subject.trackNftOwnerChange(cSN1, aAccount, bAccount);
		subject.trackTokenUnitsChange(bToken, cAccount, cOnlyBalanceChange);
		subject.trackTokenUnitsChange(aToken, aAccount, aFirstBalanceChange);
		subject.trackTokenUnitsChange(aToken, bAccount, bOnlyBalanceChange);
		subject.trackTokenUnitsChange(aToken, aAccount, aSecondBalanceChange);
		subject.trackTokenUnitsChange(aToken, bAccount, -bOnlyBalanceChange);

		final var netTokenChanges = subject.getNetTrackedTokenUnitAndOwnershipChanges();

		assertEquals(3, netTokenChanges.size());

		final var netAChanges = netTokenChanges.get(0);
		assertEquals(aToken, netAChanges.getToken());
		assertEquals(1, netAChanges.getTransfersCount());
		final var aaChange = netAChanges.getTransfers(0);
		assertEquals(aAccount, aaChange.getAccountID());
		assertEquals(aFirstBalanceChange + aSecondBalanceChange, aaChange.getAmount());

		final var netBChanges = netTokenChanges.get(1);
		assertEquals(bToken, netBChanges.getToken());
		assertEquals(1, netBChanges.getTransfersCount());
		final var bcChange = netBChanges.getTransfers(0);
		assertEquals(cAccount, bcChange.getAccountID());
		assertEquals(cOnlyBalanceChange, bcChange.getAmount());

		final var netCChanges = netTokenChanges.get(2);
		assertEquals(cToken, netCChanges.getToken());
		assertEquals(1, netCChanges.getNftTransfersCount());
		final var abcChange = netCChanges.getNftTransfers(0);
		assertEquals(aAccount, abcChange.getSenderAccountID());
		assertEquals(bAccount, abcChange.getReceiverAccountID());
		assertEquals(1L, abcChange.getSerialNumber());

		subject.reset();
		assertSame(Collections.emptyList(), subject.getNetTrackedTokenUnitAndOwnershipChanges());
	}

	@Test
	void tracksAndResetsHbarChangesAsExpected() {
		subject.trackHbarChange(cAccount, cOnlyBalanceChange);
		subject.trackHbarChange(aAccount, aFirstBalanceChange);
		subject.trackHbarChange(bAccount, bOnlyBalanceChange);
		subject.trackHbarChange(aAccount, aSecondBalanceChange);
		subject.trackHbarChange(bAccount, -bOnlyBalanceChange);

		final var netChanges = subject.getNetTrackedHbarChanges();
		assertEquals(2, netChanges.getAccountAmountsCount());
		final var aChange = netChanges.getAccountAmounts(0);
		assertEquals(aAccount, aChange.getAccountID());
		assertEquals(aFirstBalanceChange + aSecondBalanceChange, aChange.getAmount());
		final var cChange = netChanges.getAccountAmounts(1);
		assertEquals(cAccount, cChange.getAccountID());
		assertEquals(cOnlyBalanceChange, cChange.getAmount());

		subject.reset();
		assertEquals(0, subject.getNetTrackedHbarChanges().getAccountAmountsCount());
	}

	@Test
	void prioritizesExplicitTokenBalanceChanges() {
		final var aaRelChange = new TokenRelationship(
				new Token(Id.fromGrpcToken(aToken)), new Account(Id.fromGrpcAccount(aAccount)));
		aaRelChange.setBalance(aFirstBalanceChange);
		final var bbRelChange = new TokenRelationship(
				new Token(Id.fromGrpcToken(bToken)), new Account(Id.fromGrpcAccount(bAccount)));
		bbRelChange.setBalance(bOnlyBalanceChange);
		final var ccRelChange = new TokenRelationship(
				new Token(Id.fromGrpcToken(cToken)), new Account(Id.fromGrpcAccount(cAccount)));
		bbRelChange.setBalance(bOnlyBalanceChange);

	}

	private AccountAmount.Builder aaBuilderWith(final AccountID account, final long amount) {
		return AccountAmount.newBuilder().setAccountID(account).setAmount(amount);
	}

	private static final long aFirstBalanceChange = 1_000L;
	private static final long aSecondBalanceChange = 9_000L;
	private static final long bOnlyBalanceChange = 7_777L;
	private static final long cOnlyBalanceChange = 8_888L;
	private static final TokenID aToken = IdUtils.asToken("0.0.666");
	private static final TokenID bToken = IdUtils.asToken("0.0.777");
	private static final TokenID cToken = IdUtils.asToken("0.0.888");
	private static final NftId cSN1 = new NftId(0, 0, 888, 1);
	private static final AccountID aAccount = IdUtils.asAccount("0.0.12345");
	private static final AccountID bAccount = IdUtils.asAccount("0.0.23456");
	private static final AccountID cAccount = IdUtils.asAccount("0.0.34567");
}