package com.hedera.services.state;

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
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.swirlds.common.AddressBook;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.function.Supplier;

@Module
public abstract class StateModule {
	@Provides @Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts(StatesManager states) {
		return states::accounts;
	}

	@Provides @Singleton
	public Supplier<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> storage(StatesManager statesManager) {
		return statesManager::storage;
	}

	@Provides @Singleton
	public Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics(StatesManager states) {
		return states::topics;
	}

	@Provides @Singleton
	public Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens(StatesManager states) {
		return states::tokens;
	}

	@Provides @Singleton
	public Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenAssociations(StatesManager states) {
		return states::tokenAssociations;
	}

	@Provides @Singleton
	public Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules(StatesManager states) {
		return states::schedules;
	}

	@Provides @Singleton
	public Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts(StatesManager states) {
		return states::uniqueTokens;
	}

	@Provides @Singleton
	public Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByType(StatesManager states) {
		return states::uniqueTokenAssociations;
	}

	@Provides @Singleton
	public Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByOwner(StatesManager states) {
		return states::uniqueOwnershipAssociations;
	}

	@Provides @Singleton
	public Supplier<FCOneToManyRelation<PermHashInteger, Long>> treasuryNftsByType(StatesManager states) {
		return states::uniqueOwnershipTreasuryAssociations;
	}

	@Provides @Singleton
	public Supplier<MerkleDiskFs> diskFs(StatesManager states) {
		return states::diskFs;
	}

	@Provides @Singleton
	public Supplier<MerkleNetworkContext> networkCtx(StatesManager states) {
		return states::networkCtx;
	}

	@Provides @Singleton
	public Supplier<AddressBook> addressBook(StatesManager states) {
		return states::addressBook;
	}
}
