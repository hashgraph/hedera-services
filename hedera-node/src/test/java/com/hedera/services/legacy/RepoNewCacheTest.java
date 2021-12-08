package com.hedera.services.legacy;

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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.LedgerAccountsSource;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.mocks.StorageSourceFactory;
import com.hedera.test.mocks.TestContextValidator;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.CommonUtils;
import com.swirlds.merkle.map.MerkleMap;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.Source;
import org.ethereum.db.ServicesRepositoryImpl;
import org.ethereum.db.ServicesRepositoryRoot;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.mock;

@ExtendWith(MockitoExtension.class)
class RepoNewCacheTest {
	@Mock
	private AliasManager autoAccounts;
	@Mock
	private AutoCreationLogic autoAccountCreator;

	@Disabled
	public void test() {
		MerkleMap<EntityNum, MerkleAccount> accountMap = new MerkleMap<>();
		MerkleMap<String, MerkleOptionalBlob> storageMap = new MerkleMap<>();
		DbSource<byte[]> repDBFile = StorageSourceFactory.from(storageMap);

		TransactionalLedger<AccountID, AccountProperty, MerkleAccount> delegate = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				new BackingAccounts(() -> accountMap),
				new ChangeSummaryManager<>());
		HederaLedger ledger = new HederaLedger(
				mock(TokenStore.class),
				mock(EntityIdSource.class),
				mock(ExpiringCreations.class),
				TestContextValidator.TEST_VALIDATOR,
				new SideEffectsTracker(),
				mock(AccountRecordsHistorian.class),
				new MockGlobalDynamicProps(),
				delegate,
				autoAccountCreator,
				autoAccounts);
		Source<byte[], AccountState> repDatabase = new LedgerAccountsSource(ledger);
		ServicesRepositoryRoot repository = new ServicesRepositoryRoot(repDatabase, repDBFile);
		String key = CommonUtils.hex(EntityIdUtils.asSolidityAddress(0, 0, 1));
		byte[] keyByte = null;
		try {
			keyByte = CommonUtils.unhex(key);
		} catch (IllegalArgumentException ignore) {
		}
		repository.addBalance(keyByte, BigInteger.TEN);
		repository.commit();

		Repository track1 = repository.startTracking();

		Repository track2 = track1.startTracking();
		track2.addBalance(keyByte, BigInteger.TEN);
		assertEquals(20, track2.getBalance(keyByte).longValue());
		assertEquals(10, track1.getBalance(keyByte).longValue());
		assertEquals(10, repository.getBalance(keyByte).longValue());
		track2.commit();

		assertEquals(20, track2.getBalance(keyByte).longValue());
		assertEquals(20, track1.getBalance(keyByte).longValue());
		assertEquals(10, repository.getBalance(keyByte).longValue());

		track1.commit();

		assertEquals(20, track2.getBalance(keyByte).longValue());
		assertEquals(20, track1.getBalance(keyByte).longValue());
		assertEquals(20, repository.getBalance(keyByte).longValue());

		repository.commit();
		assertEquals(20, track2.getBalance(keyByte).longValue());
		assertEquals(20, track1.getBalance(keyByte).longValue());
		assertEquals(20, repository.getBalance(keyByte).longValue());

		track1.addBalance(keyByte, BigInteger.valueOf(-5l));

		assertEquals(15, track2.getBalance(keyByte).longValue());
		assertEquals(15, track1.getBalance(keyByte).longValue());
		assertEquals(20, repository.getBalance(keyByte).longValue());

		track1.commit();

		assertEquals(15, track2.getBalance(keyByte).longValue());
		assertEquals(15, track1.getBalance(keyByte).longValue());
		assertEquals(15, repository.getBalance(keyByte).longValue());
		repository.commit();

		repository.saveCode(keyByte, "Test Code for SmartContract".getBytes());

		byte[] code = repository.getCode(keyByte);
		String codeStr = new String(code);
		assertEquals("Test Code for SmartContract", codeStr);
		repository.commit();

		repository.saveCode(keyByte, "Test Code for SmartContract..New".getBytes());
		repository.commit();

		code = repository.getCode(keyByte);
	}

	@Test
	void rollbackTest() {
		MerkleMap<EntityNum, MerkleAccount> accountMap = new MerkleMap<>();
		MerkleMap<String, MerkleOptionalBlob> storageMap = new MerkleMap<>();
		DbSource<byte[]> repDBFile = StorageSourceFactory.from(storageMap);

		BackingAccounts backingAccounts = new BackingAccounts(() -> accountMap);
		TransactionalLedger<AccountID, AccountProperty, MerkleAccount> delegate = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				backingAccounts,
				new ChangeSummaryManager<>());
		MerkleAccount someAccount = new MerkleAccount();
		MerkleAccount someOtherAccount = new MerkleAccount();
		try {
			someAccount.setBalance(100_000_000L);
			someOtherAccount.setBalance(0L);
			new HederaAccountCustomizer()
					.key(new JContractIDKey(0, 0, 1))
					.customizing(someAccount);
			new HederaAccountCustomizer()
					.key(new JContractIDKey(0, 0, 2))
					.customizing(someOtherAccount);
		} catch (Exception impossible) {
		}
		backingAccounts.put(IdUtils.asAccount("0.0.1"), someAccount);
		backingAccounts.put(IdUtils.asAccount("0.0.2"), someOtherAccount);
		HederaLedger ledger = new HederaLedger(
				mock(TokenStore.class),
				mock(EntityIdSource.class),
				mock(ExpiringCreations.class),
				TestContextValidator.TEST_VALIDATOR,
				new SideEffectsTracker(),
				mock(AccountRecordsHistorian.class),
				new MockGlobalDynamicProps(),
				delegate,
				autoAccountCreator,
				autoAccounts);
		Source<byte[], AccountState> accountSource = new LedgerAccountsSource(ledger);
		ServicesRepositoryRoot repository = new ServicesRepositoryRoot(accountSource, repDBFile);

		String someKey = CommonUtils.hex(EntityIdUtils.asSolidityAddress(0, 0, 1));
		byte[] someKeyBytes = null;
		try {
			someKeyBytes = CommonUtils.unhex(someKey);
		} catch (IllegalArgumentException ignore) {
		}

		ledger.begin();
		repository.increaseNonce(someKeyBytes);
		ServicesRepositoryImpl track1 = repository.startTracking();
		track1.addBalance(someKeyBytes, BigInteger.TEN.negate());

		assertEquals(99_999_990L, track1.getBalance(someKeyBytes).longValue());
		assertEquals(100_000_000L, repository.getBalance(someKeyBytes).longValue());

		track1.rollback();

		repository.commit();
		ledger.commit();

		assertEquals(100_000_000L, repository.getBalance(someKeyBytes).longValue());
	}

}
