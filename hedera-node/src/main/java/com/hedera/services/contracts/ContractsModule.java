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
import com.hedera.services.contracts.annotations.BytecodeSource;
import com.hedera.services.contracts.annotations.StorageSource;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV19;
import com.hedera.services.contracts.operation.HederaBalanceOperation;
import com.hedera.services.contracts.operation.HederaCallCodeOperation;
import com.hedera.services.contracts.operation.HederaCallOperation;
import com.hedera.services.contracts.operation.HederaCreateOperation;
import com.hedera.services.contracts.operation.HederaDelegateCallOperation;
import com.hedera.services.contracts.operation.HederaExtCodeCopyOperation;
import com.hedera.services.contracts.operation.HederaExtCodeHashOperation;
import com.hedera.services.contracts.operation.HederaExtCodeSizeOperation;
import com.hedera.services.contracts.operation.HederaSStoreOperation;
import com.hedera.services.contracts.operation.HederaSelfDestructOperation;
import com.hedera.services.contracts.operation.HederaStaticCallOperation;
import com.hedera.services.contracts.persistence.BlobStoragePersistence;
import com.hedera.services.contracts.sources.BlobStorageSource;
import com.hedera.services.contracts.sources.LedgerAccountsSource;
import com.hedera.services.contracts.sources.SoliditySigsVerifier;
import com.hedera.services.contracts.sources.TxnAwareSoliditySigsVerifier;
import com.hedera.services.keys.StandardSyncActivationCheck;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import org.ethereum.core.AccountState;
import org.ethereum.datasource.Source;
import org.ethereum.datasource.StoragePersistence;
import org.ethereum.db.ServicesRepositoryRoot;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.InvalidOperation;
import org.hyperledger.besu.evm.operation.Operation;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Supplier;

import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.bytecodeMapFrom;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.storageMapFrom;
import static com.hedera.services.files.EntityExpiryMapFactory.entityExpiryMapFrom;

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
			Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts
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

	@Provides
	@Singleton
	@IntoSet
	public static Operation provideCreate2Operation(GasCalculator gasCalculator) {
		return new InvalidOperation(0xF5, gasCalculator);
	}

	@Binds
	@Singleton
	public abstract GasCalculator bindHederaGasCalculatorV19(GasCalculatorHederaV19 gasCalculator);

	@Binds
	@Singleton
	@IntoSet
	public abstract Operation bindBalanceOperation(HederaBalanceOperation balance);

	@Binds
	@Singleton
	@IntoSet
	public abstract Operation bindCallCodeOperation(HederaCallCodeOperation callCode);

	@Binds
	@Singleton
	@IntoSet
	public abstract Operation bindCallOperation(HederaCallOperation call);

	@Binds
	@Singleton
	@IntoSet
	public abstract Operation bindCreateOperation(HederaCreateOperation create);

	@Binds
	@Singleton
	@IntoSet
	public abstract Operation bindDelegateCallOperation(HederaDelegateCallOperation delegateCall);

	@Binds
	@Singleton
	@IntoSet
	public abstract Operation bindExtCodeCopyOperation(HederaExtCodeCopyOperation extCodeCopy);

	@Binds
	@Singleton
	@IntoSet
	public abstract Operation bindExtCodeHashOperation(HederaExtCodeHashOperation extCodeHash);

	@Binds
	@Singleton
	@IntoSet
	public abstract Operation bindExtCodeSizeOperation(HederaExtCodeSizeOperation extCodeSize);

	@Binds
	@Singleton
	@IntoSet
	public abstract Operation bindSelfDestructOperation(HederaSelfDestructOperation selfDestruct);

	@Binds
	@Singleton
	@IntoSet
	public abstract Operation bindSStoreOperation(HederaSStoreOperation sstore);

	@Binds
	@Singleton
	@IntoSet
	public abstract Operation bindStaticCallOperation(HederaStaticCallOperation staticCall);
}
