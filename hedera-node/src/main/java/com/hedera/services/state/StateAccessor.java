package com.hedera.services.state;

import com.hedera.services.ServicesState;
import com.hedera.services.context.StateChildren;
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

public class StateAccessor {
	private final StateChildren children = new StateChildren();

	public StateAccessor(ServicesState initialState) {
		updateFrom(initialState);
	}

	public void updateFrom(ServicesState state) {
		children.setAccounts(state.accounts());
		children.setTopics(state.topics());
		children.setStorage(state.storage());
		children.setTokens(state.tokens());
		children.setTokenAssociations(state.tokenAssociations());
		children.setSchedules(state.scheduleTxs());
		children.setNetworkCtx(state.networkCtx());
		children.setAddressBook(state.addressBook());
		children.setDiskFs(state.diskFs());
		children.setUniqueTokens(state.uniqueTokens());
		children.setUniqueTokenAssociations(state.uniqueTokenAssociations());
		children.setUniqueOwnershipAssociations(state.uniqueOwnershipAssociations());
		children.setUniqueOwnershipTreasuryAssociations(state.uniqueTreasuryOwnershipAssociations());
	}

	public FCMap<MerkleEntityId, MerkleAccount> accounts() {
		return children.getAccounts();
	}

	public FCMap<MerkleEntityId, MerkleTopic> topics() {
		return children.getTopics();
	}

	public FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage() {
		return children.getStorage();
	}

	public FCMap<MerkleEntityId, MerkleToken> tokens() {
		return children.getTokens();
	}

	public FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations() {
		return children.getTokenAssociations();
	}

	public FCMap<MerkleEntityId, MerkleSchedule> schedules() {
		return children.getSchedules();
	}

	public FCMap<MerkleUniqueTokenId, MerkleUniqueToken> uniqueTokens() {
		return children.getUniqueTokens();
	}

	public FCOneToManyRelation<PermHashInteger, Long> uniqueTokenAssociations() {
		return children.getUniqueTokenAssociations();
	}

	public FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipAssociations() {
		return children.getUniqueOwnershipAssociations();
	}

	public FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipTreasuryAssociations() {
		return children.getUniqueOwnershipTreasuryAssociations();
	}

	public MerkleDiskFs diskFs() {
		return children.getDiskFs();
	}

	public MerkleNetworkContext networkCtx() {
		return children.getNetworkCtx();
	}

	public AddressBook addressBook() {
		return children.getAddressBook();
	}

	public StateChildren children() {
		return children;
	}
}
