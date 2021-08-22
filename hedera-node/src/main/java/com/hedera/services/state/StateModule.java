package com.hedera.services.state;

import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.ids.SeqNoEntityIdSource;
import com.hedera.services.state.annotations.NftsByOwner;
import com.hedera.services.state.annotations.NftsByType;
import com.hedera.services.state.annotations.TreasuryNftsByType;
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
	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleAccount>> provideAccounts(WorkingState states) {
		return states::accounts;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> provideStorage(WorkingState workingState) {
		return workingState::storage;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleTopic>> provideTopics(WorkingState states) {
		return states::topics;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleToken>> provideTokens(WorkingState states) {
		return states::tokens;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> provideTokenAssociations(
			WorkingState states
	) {
		return states::tokenAssociations;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleSchedule>> provideSchedules(WorkingState states) {
		return states::schedules;
	}

	@Provides
	@Singleton
	public static Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> provideNfts(WorkingState states) {
		return states::uniqueTokens;
	}

	@Provides
	@Singleton
	@NftsByType
	public static Supplier<FCOneToManyRelation<PermHashInteger, Long>> provideNftsByType(WorkingState states) {
		return states::uniqueTokenAssociations;
	}

	@Provides
	@Singleton
	@NftsByOwner
	public static Supplier<FCOneToManyRelation<PermHashInteger, Long>> provideNftsByOwner(WorkingState states) {
		return states::uniqueOwnershipAssociations;
	}

	@Provides
	@Singleton
	@TreasuryNftsByType
	public static Supplier<FCOneToManyRelation<PermHashInteger, Long>> provideTreasuryNftsByType(WorkingState states) {
		return states::uniqueOwnershipTreasuryAssociations;
	}

	@Provides
	@Singleton
	public static Supplier<MerkleDiskFs> provideDiskFs(WorkingState states) {
		return states::diskFs;
	}

	@Provides
	@Singleton
	public static Supplier<MerkleNetworkContext> provideNetworkCtx(WorkingState states) {
		return states::networkCtx;
	}

	@Provides
	@Singleton
	public static Supplier<AddressBook> provideAddressBook(WorkingState states) {
		return states::addressBook;
	}

	@Provides
	@Singleton
	public static EntityIdSource provideEntityIdSource(WorkingState states) {
		return new SeqNoEntityIdSource(() -> states.networkCtx().seqNo());
	}

	@Provides
	@Singleton
	public static Supplier<ExchangeRates> provideMidnightRates(WorkingState states) {
		return () -> states.networkCtx().midnightRates();
	}

	@Binds
	@Singleton
	public abstract EntityCreator bindEntityCreator(ExpiringCreations creator);
}
