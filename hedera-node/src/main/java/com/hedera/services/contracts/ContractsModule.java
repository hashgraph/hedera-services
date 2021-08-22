package com.hedera.services.contracts;

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
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcmap.FCMap;
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
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
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
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
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
