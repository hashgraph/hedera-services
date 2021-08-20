package com.hedera.services.state;

import com.hedera.services.ServicesState;
import com.hedera.services.context.StateChildren;
import com.hedera.services.state.annotations.InitialState;
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

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StatesManager {
	private final StateChildren workingChildren = new StateChildren();

	@Inject
	public StatesManager(@InitialState ServicesState initialState) {
		updateWorkingState(initialState);
	}

	public void updateWorkingState(ServicesState state) {
		workingChildren.setAccounts(state.accounts());
		workingChildren.setTopics(state.topics());
		workingChildren.setStorage(state.storage());
		workingChildren.setTokens(state.tokens());
		workingChildren.setTokenAssociations(state.tokenAssociations());
		workingChildren.setSchedules(state.scheduleTxs());
		workingChildren.setNetworkCtx(state.networkCtx());
		workingChildren.setAddressBook(state.addressBook());
		workingChildren.setDiskFs(state.diskFs());
		workingChildren.setUniqueTokens(state.uniqueTokens());
		workingChildren.setUniqueTokenAssociations(state.uniqueTokenAssociations());
		workingChildren.setUniqueOwnershipAssociations(state.uniqueOwnershipAssociations());
		workingChildren.setUniqueOwnershipTreasuryAssociations(state.uniqueTreasuryOwnershipAssociations());
	}

	public FCMap<MerkleEntityId, MerkleAccount> accounts() {
		return workingChildren.getAccounts();
	}

	public FCMap<MerkleEntityId, MerkleTopic> topics() {
		return workingChildren.getTopics();
	}

	public FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage() {
		return workingChildren.getStorage();
	}

	public FCMap<MerkleEntityId, MerkleToken> tokens() {
		return workingChildren.getTokens();
	}

	public FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations() {
		return workingChildren.getTokenAssociations();
	}

	public FCMap<MerkleEntityId, MerkleSchedule> schedules() {
		return workingChildren.getSchedules();
	}

	public FCMap<MerkleUniqueTokenId, MerkleUniqueToken> uniqueTokens() {
		return workingChildren.getUniqueTokens();
	}

	public FCOneToManyRelation<PermHashInteger, Long> uniqueTokenAssociations() {
		return workingChildren.getUniqueTokenAssociations();
	}

	public FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipAssociations() {
		return workingChildren.getUniqueOwnershipAssociations();
	}

	public FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipTreasuryAssociations() {
		return workingChildren.getUniqueOwnershipTreasuryAssociations();
	}

	public MerkleDiskFs diskFs() {
		return workingChildren.getDiskFs();
	}

	public MerkleNetworkContext networkCtx() {
		return workingChildren.getNetworkCtx();
	}

	public AddressBook addressBook() {
		return workingChildren.getAddressBook();
	}
}
