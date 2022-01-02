package com.hedera.services.context;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.AddressBook;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;

import java.time.Instant;

public interface StateChildren {
	Instant getSignedAt();

	default boolean isSignedAfter(final StateChildren children) {
		return getSignedAt().isAfter(children.getSignedAt());
	}

	default boolean isSigned() {
		return getSignedAt().isAfter(Instant.EPOCH);
	}

	MerkleMap<EntityNum, MerkleAccount> getAccounts();

	MerkleMap<EntityNum, MerkleTopic> getTopics();

	MerkleMap<EntityNum, MerkleToken> getTokens();

	MerkleMap<EntityNum, MerkleSchedule> getSchedules();

	MerkleMap<String, MerkleOptionalBlob> getStorage();

	MerkleMap<EntityNumPair, MerkleTokenRelStatus> getTokenAssociations();

	MerkleNetworkContext getNetworkCtx();

	AddressBook getAddressBook();

	MerkleSpecialFiles getSpecialFiles();

	MerkleMap<EntityNumPair, MerkleUniqueToken> getUniqueTokens();

	FCOneToManyRelation<EntityNum, Long> getUniqueTokenAssociations();

	FCOneToManyRelation<EntityNum, Long> getUniqueOwnershipAssociations();

	FCOneToManyRelation<EntityNum, Long> getUniqueOwnershipTreasuryAssociations();

	RecordsRunningHashLeaf getRunningHashLeaf();
}
