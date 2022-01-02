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
	Instant signedAt();

	default boolean isSignedAfter(final StateChildren children) {
		return signedAt().isAfter(children.signedAt());
	}

	default boolean isSigned() {
		return signedAt().isAfter(Instant.EPOCH);
	}

	MerkleMap<EntityNum, MerkleAccount> accounts();

	MerkleMap<EntityNum, MerkleTopic> topics();

	MerkleMap<EntityNum, MerkleToken> tokens();

	MerkleMap<EntityNum, MerkleSchedule> schedules();

	MerkleMap<String, MerkleOptionalBlob> storage();

	MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations();

	MerkleNetworkContext networkCtx();

	AddressBook addressBook();

	MerkleSpecialFiles specialFiles();

	MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens();

	FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations();

	FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations();

	FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations();

	RecordsRunningHashLeaf runningHashLeaf();
}
