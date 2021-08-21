package com.hedera.services.state;

import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.ids.SeqNoEntityIdSource;
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
	public static Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts(ActiveStates states) {
		return states::accounts;
	}

	@Provides @Singleton
	public static Supplier<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> storage(ActiveStates activeStates) {
		return activeStates::storage;
	}

	@Provides @Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics(ActiveStates states) {
		return states::topics;
	}

	@Provides @Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens(ActiveStates states) {
		return states::tokens;
	}

	@Provides @Singleton
	public static Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenAssociations(ActiveStates states) {
		return states::tokenAssociations;
	}

	@Provides @Singleton
	public static Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules(ActiveStates states) {
		return states::schedules;
	}

	@Provides @Singleton
	public static Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts(ActiveStates states) {
		return states::uniqueTokens;
	}

	@Provides @Singleton
	public static Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByType(ActiveStates states) {
		return states::uniqueTokenAssociations;
	}

	@Provides @Singleton
	public static Supplier<FCOneToManyRelation<PermHashInteger, Long>> nftsByOwner(ActiveStates states) {
		return states::uniqueOwnershipAssociations;
	}

	@Provides @Singleton
	public static Supplier<FCOneToManyRelation<PermHashInteger, Long>> treasuryNftsByType(ActiveStates states) {
		return states::uniqueOwnershipTreasuryAssociations;
	}

	@Provides @Singleton
	public static Supplier<MerkleDiskFs> diskFs(ActiveStates states) {
		return states::diskFs;
	}

	@Provides @Singleton
	public static Supplier<MerkleNetworkContext> networkCtx(ActiveStates states) {
		return states::networkCtx;
	}

	@Provides @Singleton
	public static Supplier<AddressBook> addressBook(ActiveStates states) {
		return states::addressBook;
	}

	@Provides @Singleton
	public static EntityIdSource ids(ActiveStates states) {
		return new SeqNoEntityIdSource(() -> states.networkCtx().seqNo());
	}
}
