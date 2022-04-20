package com.hedera.services.state.migration;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.RandomExtended;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.services.state.migration.ReleaseTwentySixMigration.INSERTIONS_PER_COPY;
import static com.hedera.services.state.migration.ReleaseTwentySixMigration.SEVEN_DAYS_IN_SECONDS;
import static com.hedera.services.state.migration.ReleaseTwentySixMigration.THREAD_COUNT;
import static com.hedera.services.state.migration.ReleaseTwentySixMigration.grantFreeAutoRenew;
import static com.hedera.services.state.migration.ReleaseTwentySixMigration.makeStorageIterable;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReleaseTwentySixMigrationTest {
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private VirtualMap<ContractKey, ContractValue> contractStorage;
	@Mock
	private VirtualMap<ContractKey, IterableContractValue> iterableContractStorage;
	@Mock
	private VirtualMap<ContractKey, IterableContractValue> finalContractStorage;
	@Mock
	private ServicesState initializingState;
	@Mock
	private KvPairIterationMigrator migrator;
	@Mock
	private ReleaseTwentySixMigration.MigratorFactory migratorFactory;
	@Mock
	private ReleaseTwentySixMigration.MigrationUtility migrationUtility;
	@Mock
	private MerkleAccount merkleAccount;

	@Test
	void migratesToIterableStorageAsExpected() throws InterruptedException {
		given(initializingState.accounts()).willReturn(accounts);
		given(initializingState.getChild(StateChildIndices.CONTRACT_STORAGE)).willReturn(contractStorage);
		given(migratorFactory.from(
				eq(INSERTIONS_PER_COPY),
				eq(accounts),
				any(SizeLimitedStorage.IterableStorageUpserter.class),
				eq(iterableContractStorage))).willReturn(migrator);
		given(migrator.getMigratedStorage()).willReturn(finalContractStorage);

		makeStorageIterable(initializingState, migratorFactory, migrationUtility, iterableContractStorage);

		verify(migrationUtility).extractVirtualMapData(contractStorage, migrator, THREAD_COUNT);
		verify(migrator).finish();
		verify(initializingState).setChild(StateChildIndices.CONTRACT_STORAGE, finalContractStorage);
	}

	@Test
	void translatesInterruptedExceptionToIse() throws InterruptedException {
		given(initializingState.accounts()).willReturn(accounts);
		given(initializingState.getChild(StateChildIndices.CONTRACT_STORAGE)).willReturn(contractStorage);
		given(migratorFactory.from(
				eq(INSERTIONS_PER_COPY),
				eq(accounts),
				any(SizeLimitedStorage.IterableStorageUpserter.class),
				eq(iterableContractStorage))).willReturn(migrator);
		willThrow(InterruptedException.class).given(migrationUtility)
				.extractVirtualMapData(contractStorage, migrator, THREAD_COUNT);

		Assertions.assertThrows(IllegalStateException.class, () ->
				makeStorageIterable(initializingState, migratorFactory, migrationUtility, iterableContractStorage));
	}

	@Test
	void grantsAutoRenewToContracts() {
		final var accountsMap = new MerkleMap<EntityNum, MerkleAccount>();
		accountsMap.put(EntityNum.fromLong(1L), merkleAccount);
		accountsMap.put(EntityNum.fromLong(2L), merkleAccount);
		final var instant = Instant.ofEpochSecond(123456789L);

		final var rand = new RandomExtended(8682588012L);

		given(initializingState.accounts()).willReturn(accountsMap);

		final var account = new MerkleAccount();
		account.setSmartContract(true);
		account.setExpiry(1234L);
		account.setKey(EntityNum.fromLong(1L));

		given(merkleAccount.getExpiry()).willReturn(1234L).willReturn(2345L);
		given(merkleAccount.cast()).willReturn(account);

		grantFreeAutoRenew(initializingState, instant);

		final var expectedExpiry1 = getExpectedExpiry(1234L, instant.getEpochSecond(), rand);
		final var expectedExpiry2 = getExpectedExpiry(2345L, instant.getEpochSecond(), rand);

		verify(merkleAccount).setExpiry(expectedExpiry1);
		verify(merkleAccount).setExpiry(expectedExpiry2);
	}

	private long getExpectedExpiry(final long currentExpiry, final long instant, final RandomExtended rand) {
		return Math.max(currentExpiry,
				         instant
						+ THREE_MONTHS_IN_SECONDS
						+ rand.nextLong(0, SEVEN_DAYS_IN_SECONDS));
	}
}
