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
public class ImmutableStateChildren implements StateChildren {
	private final MerkleMap<EntityNum, MerkleAccount> accounts;
	private final MerkleMap<EntityNum, MerkleTopic> topics;
	private final MerkleMap<EntityNum, MerkleToken> tokens;
	private final MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens;
	private final MerkleMap<EntityNum, MerkleSchedule> schedules;
	private final MerkleMap<String, MerkleOptionalBlob> storage;
	private final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations;
	private final FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations;
	private final FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations;
	private final FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations;
	private final MerkleNetworkContext networkCtx;
	private final AddressBook addressBook;
	private final MerkleSpecialFiles specialFiles;
	private final RecordsRunningHashLeaf runningHashLeaf;
	private final Instant signedAt;

	public ImmutableStateChildren(
			final MerkleMap<EntityNum, MerkleAccount> accounts,
			final MerkleMap<EntityNum, MerkleTopic> topics,
			final MerkleMap<EntityNum, MerkleToken> tokens,
			final MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens,
			final MerkleMap<EntityNum, MerkleSchedule> schedules,
			final MerkleMap<String, MerkleOptionalBlob> storage,
			final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations,
			final FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations,
			final FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations,
			final FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations,
			final MerkleNetworkContext networkCtx, AddressBook addressBook,
			final MerkleSpecialFiles specialFiles, RecordsRunningHashLeaf runningHashLeaf, Instant signedAt
	) {
		this.accounts = accounts;
		this.topics = topics;
		this.tokens = tokens;
		this.uniqueTokens = uniqueTokens;
		this.schedules = schedules;
		this.storage = storage;
		this.tokenAssociations = tokenAssociations;
		this.uniqueTokenAssociations = uniqueTokenAssociations;
		this.uniqueOwnershipAssociations = uniqueOwnershipAssociations;
		this.uniqueOwnershipTreasuryAssociations = uniqueOwnershipTreasuryAssociations;
		this.networkCtx = networkCtx;
		this.addressBook = addressBook;
		this.specialFiles = specialFiles;
		this.runningHashLeaf = runningHashLeaf;
		this.signedAt = signedAt;
	}

	@Override
	public Instant getSignedAt() {
		return signedAt;
	}

	@Override
	public MerkleMap<EntityNum, MerkleAccount> getAccounts() {
		return accounts;
	}

	@Override
	public MerkleMap<EntityNum, MerkleTopic> getTopics() {
		return topics;
	}

	@Override
	public MerkleMap<EntityNum, MerkleToken> getTokens() {
		return tokens;
	}

	@Override
	public MerkleMap<EntityNum, MerkleSchedule> getSchedules() {
		return schedules;
	}

	@Override
	public MerkleMap<String, MerkleOptionalBlob> getStorage() {
		return storage;
	}

	@Override
	public MerkleMap<EntityNumPair, MerkleTokenRelStatus> getTokenAssociations() {
		return tokenAssociations;
	}

	@Override
	public MerkleNetworkContext getNetworkCtx() {
		return networkCtx;
	}

	@Override
	public AddressBook getAddressBook() {
		return addressBook;
	}

	@Override
	public MerkleSpecialFiles getSpecialFiles() {
		return specialFiles;
	}

	@Override
	public MerkleMap<EntityNumPair, MerkleUniqueToken> getUniqueTokens() {
		return uniqueTokens;
	}

	@Override
	public FCOneToManyRelation<EntityNum, Long> getUniqueTokenAssociations() {
		return uniqueTokenAssociations;
	}

	@Override
	public FCOneToManyRelation<EntityNum, Long> getUniqueOwnershipAssociations() {
		return uniqueOwnershipAssociations;
	}

	@Override
	public FCOneToManyRelation<EntityNum, Long> getUniqueOwnershipTreasuryAssociations() {
		return uniqueOwnershipTreasuryAssociations;
	}

	@Override
	public RecordsRunningHashLeaf getRunningHashLeaf() {
		return runningHashLeaf;
	}
}
