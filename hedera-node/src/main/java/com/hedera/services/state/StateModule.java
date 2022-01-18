package com.hedera.services.state;

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

import com.hedera.services.config.NetworkInfo;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.keys.LegacyEd25519KeyReader;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.ids.SeqNoEntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.annotations.NftsByOwner;
import com.hedera.services.state.annotations.NftsByType;
import com.hedera.services.state.annotations.TreasuryNftsByType;
import com.hedera.services.state.annotations.WorkingState;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.exports.AccountsExporter;
import com.hedera.services.state.exports.BalancesExporter;
import com.hedera.services.state.exports.SignedStateBalancesExporter;
import com.hedera.services.state.exports.ToStringAccountsExporter;
import com.hedera.services.state.forensics.IssListener;
import com.hedera.services.state.initialization.BackedSystemAccountsCreator;
import com.hedera.services.state.initialization.HfsSystemFilesManager;
import com.hedera.services.state.initialization.SystemAccountsCreator;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.logic.HandleLogicModule;
import com.hedera.services.state.logic.ReconnectListener;
import com.hedera.services.state.logic.StateWriteToDiskListener;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.state.validation.BasedLedgerValidator;
import com.hedera.services.state.validation.LedgerValidator;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqTokenViewFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.JvmSystemExits;
import com.hedera.services.utils.NamedDigestFactory;
import com.hedera.services.utils.Pause;
import com.hedera.services.utils.SleepingPause;
import com.hedera.services.utils.SystemExits;
import com.swirlds.common.AddressBook;
import com.swirlds.common.InvalidSignedStateListener;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.hedera.services.utils.MiscUtils.lookupInCustomStore;

@Module(includes = HandleLogicModule.class)
public abstract class StateModule {
	@Binds
	@Singleton
	public abstract SystemExits bindSystemExits(JvmSystemExits systemExits);

	@Binds
	@Singleton
	public abstract ReconnectCompleteListener bindReconnectListener(ReconnectListener reconnectListener);

	@Binds
	@Singleton
	public abstract StateWriteToDiskCompleteListener bindStateWrittenToDiskListener(
			StateWriteToDiskListener stateWriteToDiskListener);

	@Binds
	@Singleton
	public abstract LedgerValidator bindLedgerValidator(BasedLedgerValidator basedLedgerValidator);

	@Binds
	@Singleton
	public abstract EntityCreator bindEntityCreator(ExpiringCreations creator);

	@Binds
	@Singleton
	public abstract BalancesExporter bindBalancesExporter(SignedStateBalancesExporter signedStateBalancesExporter);

	@Binds
	@Singleton
	public abstract SystemFilesManager bindSysFilesManager(HfsSystemFilesManager hfsSystemFilesManager);

	@Binds
	@Singleton
	public abstract AccountsExporter bindAccountsExporter(ToStringAccountsExporter toStringAccountsExporter);

	@Binds
	@Singleton
	public abstract SystemAccountsCreator bindSystemAccountsCreator(BackedSystemAccountsCreator backedCreator);

	@Binds
	@Singleton
	public abstract InvalidSignedStateListener bindIssListener(IssListener issListener);

	@Provides
	@Singleton
	public static VirtualMapFactory provideVirtualMapFactory() {
		return new VirtualMapFactory(JasperDbBuilder::new);
	}

	@Provides
	@Singleton
	public static Pause providePause() {
		return SleepingPause.SLEEPING_PAUSE;
	}

	@Provides
	@Singleton
	public static Supplier<Charset> provideNativeCharset() {
		return Charset::defaultCharset;
	}

	@Provides
	@Singleton
	public static NamedDigestFactory provideDigestFactory() {
		return MessageDigest::getInstance;
	}

	@Provides
	@Singleton
	public static Supplier<NotificationEngine> provideNotificationEngine() {
		return NotificationFactory::getEngine;
	}

	@Provides
	@Singleton
	public static Optional<PrintStream> providePrintStream(Platform platform) {
		final var console = platform.createConsole(true);
		return Optional.ofNullable(console).map(c -> c.out);
	}

	@Provides
	@Singleton
	public static UnaryOperator<byte[]> provideSigner(Platform platform) {
		return platform::sign;
	}

	@Provides
	@Singleton
	public static NodeId provideNodeId(Platform platform) {
		return platform.getSelfId();
	}

	@Provides
	@Singleton
	public static StateView provideCurrentView(
			TokenStore tokenStore,
			ScheduleStore scheduleStore,
			UniqTokenViewFactory uniqTokenViewFactory,
			@WorkingState StateAccessor workingState,
			NetworkInfo networkInfo
	) {
		return new StateView(
				tokenStore,
				scheduleStore,
				workingState.children(),
				uniqTokenViewFactory,
				networkInfo);
	}

	@Provides
	@Singleton
	public static Supplier<StateView> provideStateViews(
			TokenStore tokenStore,
			ScheduleStore scheduleStore,
			UniqTokenViewFactory uniqTokenViewFactory,
			@WorkingState StateAccessor workingState,
			NetworkInfo networkInfo
	) {
		return () -> new StateView(
				tokenStore,
				scheduleStore,
				workingState.children(),
				uniqTokenViewFactory,
				networkInfo);
	}

	@Provides
	@Singleton
	@WorkingState
	public static StateAccessor provideWorkingState() {
		return new StateAccessor();
	}

	@Provides
	@Singleton
	public static Supplier<MerkleMap<EntityNum, MerkleAccount>> provideWorkingAccounts(
			@WorkingState StateAccessor accessor
	) {
		return accessor::accounts;
	}

	@Provides
	@Singleton
	public static Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> provideWorkingStorage(
			@WorkingState StateAccessor accessor
	) {
		return accessor::storage;
	}

	@Provides
	@Singleton
	public static Supplier<MerkleMap<EntityNum, MerkleTopic>> provideWorkingTopics(
			@WorkingState StateAccessor accessor
	) {
		return accessor::topics;
	}

	@Provides
	@Singleton
	public static Supplier<MerkleMap<EntityNum, MerkleToken>> provideWorkingTokens(
			@WorkingState StateAccessor accessor
	) {
		return accessor::tokens;
	}

	@Provides
	@Singleton
	public static Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> provideWorkingTokenAssociations(
			@WorkingState StateAccessor accessor
	) {
		return accessor::tokenAssociations;
	}

	@Provides
	@Singleton
	public static Supplier<MerkleMap<EntityNum, MerkleSchedule>> provideWorkingSchedules(
			@WorkingState StateAccessor accessor
	) {
		return accessor::schedules;
	}

	@Provides
	@Singleton
	public static Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> provideWorkingNfts(
			@WorkingState StateAccessor accessor
	) {
		return accessor::uniqueTokens;
	}

	@Provides
	@Singleton
	@NftsByType
	public static Supplier<FCOneToManyRelation<EntityNum, Long>> provideWorkingNftsByType(
			@WorkingState StateAccessor accessor
	) {
		return accessor::uniqueTokenAssociations;
	}

	@Provides
	@Singleton
	@NftsByOwner
	public static Supplier<FCOneToManyRelation<EntityNum, Long>> provideWorkingNftsByOwner(
			@WorkingState StateAccessor accessor
	) {
		return accessor::uniqueOwnershipAssociations;
	}

	@Provides
	@Singleton
	@TreasuryNftsByType
	public static Supplier<FCOneToManyRelation<EntityNum, Long>> provideWorkingTreasuryNftsByType(
			@WorkingState StateAccessor accessor
	) {
		return accessor::uniqueOwnershipTreasuryAssociations;
	}

	@Provides
	@Singleton
	public static Supplier<MerkleSpecialFiles> provideWorkingSpecialFiles(
			@WorkingState StateAccessor accessor
	) {
		return accessor::specialFiles;
	}

	@Provides
	@Singleton
	public static Supplier<VirtualMap<ContractKey, ContractValue>> provideWorkingContractStorage(
			@WorkingState StateAccessor accessor
	) {
		return accessor::contractStorage;
	}

	@Provides
	@Singleton
	public static Supplier<MerkleNetworkContext> provideWorkingNetworkCtx(
			@WorkingState StateAccessor accessor
	) {
		return accessor::networkCtx;
	}

	@Provides
	@Singleton
	public static Supplier<AddressBook> provideWorkingAddressBook(
			@WorkingState StateAccessor accessor
	) {
		return accessor::addressBook;
	}

	@Provides
	@Singleton
	public static EntityIdSource provideWorkingEntityIdSource(
			@WorkingState StateAccessor accessor
	) {
		return new SeqNoEntityIdSource(() -> accessor.networkCtx().seqNo());
	}

	@Provides
	@Singleton
	public static Supplier<ExchangeRates> provideWorkingMidnightRates(
			@WorkingState StateAccessor accessor
	) {
		return () -> accessor.networkCtx().midnightRates();
	}

	@Provides
	@Singleton
	public static Supplier<SequenceNumber> provideWorkingSeqNo(
			@WorkingState StateAccessor accessor
	) {
		return () -> accessor.networkCtx().seqNo();
	}

	@Provides
	@Singleton
	public static Supplier<JKey> provideSystemFileKey(
			LegacyEd25519KeyReader b64KeyReader,
			@CompositeProps PropertySource properties
	) {
		return () -> lookupInCustomStore(
				b64KeyReader,
				properties.getStringProperty("bootstrap.genesisB64Keystore.path"),
				properties.getStringProperty("bootstrap.genesisB64Keystore.keyName"));
	}
}
