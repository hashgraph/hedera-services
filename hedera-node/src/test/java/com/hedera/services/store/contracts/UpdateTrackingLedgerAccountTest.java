package com.hedera.services.store.contracts;

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

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UpdateTrackingLedgerAccountTest {
	private static final long expiry = 1_234_567L;
	private static final long autoRenewPeriod = 7776000L;
	private static final long newBalance = 200_000L;
	private static final long initialBalance = 100_000L;
	private static final EntityId proxyId = new EntityId(0, 0, 54321);
	private static final AccountID targetId = IdUtils.asAccount("0.0.12345");
	private static final Address targetAddress = EntityIdUtils.asTypedSolidityAddress(targetId);

	@Mock
	private EntityIdSource ids;
	@Mock
	private EntityAccess entityAccess;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> trackingAccounts;

	private HederaWorldState parentState;

	@BeforeEach
	void setUp() {
		parentState = new HederaWorldState(ids, entityAccess);
	}

	@Test
	void mirrorsBalanceChangesInNonNullTrackingAccounts() {
		final var account = parentState.new WorldStateAccount(
				targetAddress, Wei.of(initialBalance), expiry, autoRenewPeriod, proxyId);

		final var subject = new UpdateTrackingLedgerAccount(account, trackingAccounts);

		subject.setBalance(Wei.of(newBalance));

		assertEquals(newBalance, subject.getBalance().toLong());
		verify(trackingAccounts).set(targetId, AccountProperty.BALANCE, newBalance);
	}

	@Test
	void justPropagatesBalanceChangeWithNullTrackingAccounts() {
		final var account = parentState.new WorldStateAccount(
				targetAddress, Wei.of(initialBalance), expiry, autoRenewPeriod, proxyId);

		final var subject = new UpdateTrackingLedgerAccount(account, null);

		subject.setBalance(Wei.of(newBalance));

		assertEquals(newBalance, subject.getBalance().toLong());
	}
}
