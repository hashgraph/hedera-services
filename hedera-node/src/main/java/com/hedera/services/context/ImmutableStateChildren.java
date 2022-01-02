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

/**
 * A {@link StateChildren} implementation used to capture a signed state, whose children are necessarily immutable.
 *
 * This class is thread-safe as long as it is published safely (e.g., via a {@code volatile} field, a field with
 * normal locking, or via a concurrent collection).
 */
public record ImmutableStateChildren(
		MerkleMap<EntityNum, MerkleAccount> accounts,
		MerkleMap<EntityNum, MerkleTopic> topics,
		MerkleMap<EntityNum, MerkleToken> tokens,
		MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens,
		MerkleMap<EntityNum, MerkleSchedule> schedules,
		MerkleMap<String, MerkleOptionalBlob> storage,
		MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations,
		FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations,
		FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations,
		FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations,
		MerkleNetworkContext networkCtx, AddressBook addressBook,
		MerkleSpecialFiles specialFiles,
		RecordsRunningHashLeaf runningHashLeaf,
		Instant signedAt) implements StateChildren {
}
