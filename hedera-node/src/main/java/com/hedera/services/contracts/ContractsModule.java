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
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.store.StoresModule;
import com.hedera.services.store.contracts.EntityAccess;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.MutableEntityAccess;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.virtualmap.VirtualMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.InvalidOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import javax.inject.Singleton;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.bytecodeMapFrom;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.storageMapFrom;
import static com.hedera.services.files.EntityExpiryMapFactory.entityExpiryMapFrom;

@Module(includes = {
		StoresModule.class
})
public abstract class ContractsModule {
	@Binds
	@Singleton
	public abstract HederaMutableWorldState provideMutableWorldState(HederaWorldState hederaWorldState);

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
	public static EntityAccess provideMutableEntityAccess(
			final HederaLedger ledger,
			final TransactionContext txnCtx,
			final SizeLimitedStorage storage,
			final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger,
			final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode
	) {
		return new MutableEntityAccess(ledger, txnCtx, storage, tokensLedger, bytecode);
	}

	@Provides
	@Singleton
	@IntoSet
	public static Operation provideCreate2Operation(GasCalculator gasCalculator) {
		return new InvalidOperation(0xF5, gasCalculator);
	}

	@Binds
	@Singleton
	public abstract GasCalculator bindHederaGasCalculatorV20(GasCalculatorHederaV22 gasCalculator);

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


	@Binds
	@Singleton
	@IntoMap
	@StringKey("0x167")
	public abstract PrecompiledContract bindHTSPrecompile(HTSPrecompiledContract htsPrecompiledContract);


	@Provides
	@Singleton
	public static BiPredicate<Address, MessageFrame> provideAddressValidator(
			Map<String, PrecompiledContract> precompiledContractMap) {
		Set<Address> precompiledAddresses =
				precompiledContractMap.keySet().stream().map(Address::fromHexString).collect(Collectors.toSet());
		return (address, frame) -> precompiledAddresses.contains(address) ||
				frame.getWorldUpdater().get(address) != null;
	}
}
