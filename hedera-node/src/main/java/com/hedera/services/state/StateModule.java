package com.hedera.services.state;

import com.hedera.services.ServicesState;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.ids.SeqNoEntityIdSource;
import com.hedera.services.state.annotations.NftsByOwner;
import com.hedera.services.state.annotations.NftsByType;
import com.hedera.services.state.annotations.TreasuryNftsByType;
import com.hedera.services.state.annotations.WorkingState;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqTokenViewFactory;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.swirlds.common.AddressBook;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.function.Supplier;

@Module
public abstract class StateModule {
	@Binds
	@Singleton
	public abstract EntityCreator bindEntityCreator(ExpiringCreations creator);

	@Provides
	@Singleton
	public static StateView provideCurrentView(
			TokenStore tokenStore,
			ScheduleStore scheduleStore,
			NodeLocalProperties nodeLocalProperties,
			UniqTokenViewFactory uniqTokenViewFactory,
			@WorkingState StateAccessor workingState
	) {
		return new StateView(
				tokenStore,
				scheduleStore,
				nodeLocalProperties,
				workingState.children(),
				uniqTokenViewFactory);
	}

	@Provides
	@Singleton
	public static Supplier<StateView> provideStateViews(
			TokenStore tokenStore,
			ScheduleStore scheduleStore,
			NodeLocalProperties nodeLocalProperties,
			UniqTokenViewFactory uniqTokenViewFactory,
			@WorkingState StateAccessor workingState
	) {
		return () -> new StateView(
				tokenStore,
				scheduleStore,
				nodeLocalProperties,
				workingState.children(),
				uniqTokenViewFactory);
	}

	@Provides
	@Singleton
	@WorkingState
	public static StateAccessor provideWorkingState(ServicesState initialState) {
		return new StateAccessor(initialState);
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleAccount>> provideWorkingAccounts(
			@WorkingState StateAccessor accessor
	) {
		return accessor::accounts;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> provideWorkingStorage(
			@WorkingState StateAccessor accessor
	) {
		return accessor::storage;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleTopic>> provideWorkingTopics(
			@WorkingState StateAccessor accessor
	) {
		return accessor::topics;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleToken>> provideWorkingTokens(
			@WorkingState StateAccessor accessor
	) {
		return accessor::tokens;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> provideWorkingTokenAssociations(
			@WorkingState StateAccessor accessor
	) {
		return accessor::tokenAssociations;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleSchedule>> provideWorkingSchedules(
			@WorkingState StateAccessor accessor
	) {
		return accessor::schedules;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> provideWorkingNfts(
			@WorkingState StateAccessor accessor
	) {
		return accessor::uniqueTokens;
	}

	@Provides
	@Singleton
	@NftsByType
	public static Supplier<FCOneToManyRelation<PermHashInteger, Long>> provideWorkingNftsByType(
			@WorkingState StateAccessor accessor
	) {
		return accessor::uniqueTokenAssociations;
	}

	@Provides
	@Singleton
	@NftsByOwner
	public static Supplier<FCOneToManyRelation<PermHashInteger, Long>> provideWorkingNftsByOwner(
			@WorkingState StateAccessor accessor
	) {
		return accessor::uniqueOwnershipAssociations;
	}

	@Provides
	@Singleton
	@TreasuryNftsByType
	public static Supplier<FCOneToManyRelation<PermHashInteger, Long>> provideWorkingTreasuryNftsByType(
			@WorkingState StateAccessor accessor
	) {
		return accessor::uniqueOwnershipTreasuryAssociations;
	}

	@Provides
	@Singleton
	public static Supplier<MerkleDiskFs> provideWorkingDiskFs(
			@WorkingState StateAccessor accessor
	) {
		return accessor::diskFs;
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
}
