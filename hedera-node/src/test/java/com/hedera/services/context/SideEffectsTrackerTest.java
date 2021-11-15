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

import com.hedera.services.store.models.NftId;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SideEffectsTrackerTest {
	private SideEffectsTracker subject;

	@BeforeEach
	void setUp() {
		subject = new SideEffectsTracker();
	}

	@Test
	void tracksAndResetsTokenUnitAndOwnershipChangesAsExpected() {
		subject.nftOwnerChange(cSN1, aAccount, bAccount);
		subject.tokenUnitsChange(bToken, cAccount, cOnlyBalanceChange);
		subject.tokenUnitsChange(aToken, aAccount, aFirstBalanceChange);
		subject.tokenUnitsChange(aToken, bAccount, bOnlyBalanceChange);
		subject.tokenUnitsChange(aToken, aAccount, aSecondBalanceChange);
		subject.tokenUnitsChange(aToken, bAccount, -bOnlyBalanceChange);

		final var netTokenChanges = subject.computeNetTokenUnitAndOwnershipChanges();

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
		assertSame(Collections.emptyList(), subject.computeNetTokenUnitAndOwnershipChanges());
	}

	@Test
	public void tracksAndResetsHbarChangesAsExpected() {
		subject.hbarChange(cAccount, cOnlyBalanceChange);
		subject.hbarChange(aAccount, aFirstBalanceChange);
		subject.hbarChange(bAccount, bOnlyBalanceChange);
		subject.hbarChange(aAccount, aSecondBalanceChange);
		subject.hbarChange(bAccount, -bOnlyBalanceChange);

		final var netChanges = subject.computeNetHbarChanges();
		assertEquals(2, netChanges.getAccountAmountsCount());
		final var aChange = netChanges.getAccountAmounts(0);
		assertEquals(aAccount, aChange.getAccountID());
		assertEquals(aFirstBalanceChange + aSecondBalanceChange, aChange.getAmount());
		final var cChange = netChanges.getAccountAmounts(1);
		assertEquals(cAccount, cChange.getAccountID());
		assertEquals(cOnlyBalanceChange, cChange.getAmount());

		subject.reset();
		assertEquals(0, subject.computeNetHbarChanges().getAccountAmountsCount());
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