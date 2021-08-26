package com.hedera.services.contracts;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.annotations.BytecodeSource;
import com.hedera.services.contracts.annotations.StorageSource;
import com.hedera.services.contracts.execution.SoliditySigsVerifier;
import com.hedera.services.contracts.execution.TxnAwareSoliditySigsVerifier;
import com.hedera.services.contracts.persistence.BlobStoragePersistence;
import com.hedera.services.contracts.sources.BlobStorageSource;
import com.hedera.services.contracts.sources.LedgerAccountsSource;
import com.hedera.services.keys.StandardSyncActivationCheck;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.PureBackingAccounts;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import org.ethereum.core.AccountState;
import org.ethereum.datasource.Source;
import org.ethereum.datasource.StoragePersistence;
import org.ethereum.db.ServicesRepositoryRoot;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.bytecodeMapFrom;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.storageMapFrom;
import static com.hedera.services.files.EntityExpiryMapFactory.entityExpiryMapFrom;
import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.services.records.NoopRecordsHistorian.NOOP_RECORDS_HISTORIAN;
import static com.hedera.services.state.expiry.NoopExpiringCreations.NOOP_EXPIRING_CREATIONS;
import static com.hedera.services.store.tokens.ExceptionalTokenStore.NOOP_TOKEN_STORE;

@Module
public abstract class ContractsModule {
	@Binds
	@Singleton
	public abstract Source<byte[], byte[]> bindByteCodeSource(BlobStorageSource blobStorageSource);

	@Binds
	@Singleton
	public abstract Source<byte[], AccountState> bindAccountsSource(LedgerAccountsSource ledgerAccountsSource);

	@Binds
	@Singleton
	public abstract StoragePersistence bindStoragePersistence(BlobStoragePersistence blobStoragePersistence);

	@Provides
	@Singleton
	public static SoliditySigsVerifier provideSoliditySigsVerifier(
			SyncVerifier syncVerifier,
			TransactionContext txnCtx,
			Supplier<MerkleMap<PermHashInteger, MerkleAccount>> accounts
	) {
		return new TxnAwareSoliditySigsVerifier(
				syncVerifier,
				txnCtx,
				StandardSyncActivationCheck::allKeysAreActive,
				accounts);
	}

	@Provides
	@Singleton
	public static ServicesRepositoryRoot provideServicesRepositoryRoot(
			StoragePersistence storagePersistence,
			Source<byte[], byte[]> bytecodeSource,
			Source<byte[], AccountState> accountSource
	) {
		final var repository = new ServicesRepositoryRoot(accountSource, bytecodeSource);
		repository.setStoragePersistence(storagePersistence);
		return repository;
	}

	@Provides
	@Singleton
	public static Supplier<ServicesRepositoryRoot> providePureServicesRepositoryRoots(
			OptionValidator validator,
			StoragePersistence storagePersistence,
			Source<byte[], byte[]> bytecodeSource,
			GlobalDynamicProperties dynamicProperties,
			Supplier<MerkleMap<PermHashInteger, MerkleAccount>> accounts
	) {
		final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> pureDelegate = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				new PureBackingAccounts(accounts),
				new ChangeSummaryManager<>());
		final var pureLedger = new HederaLedger(
				NOOP_TOKEN_STORE,
				NOOP_ID_SOURCE,
				NOOP_EXPIRING_CREATIONS,
				validator,
				NOOP_RECORDS_HISTORIAN,
				dynamicProperties,
				pureDelegate);
		final var pureAccountSource = new LedgerAccountsSource(pureLedger);
		return () -> {
			var pureRepository = new ServicesRepositoryRoot(pureAccountSource, bytecodeSource);
			pureRepository.setStoragePersistence(storagePersistence);
			return pureRepository;
		};
	}

	@Provides
	@Singleton
	@BytecodeSource
	public static Map<byte[], byte[]> provideBytecodeSource(Map<String, byte[]> blobStore) {
		return bytecodeMapFrom(blobStore);
	}

	@Provides
	@Singleton
	@StorageSource
	public static Map<byte[], byte[]> provideStorageSource(Map<String, byte[]> blobStore) {
		return storageMapFrom(blobStore);
	}

	@Provides
	@Singleton
	public static Map<EntityId, Long> provideEntityExpiries(Map<String, byte[]> blobStore) {
		return entityExpiryMapFrom(blobStore);
	}
}
