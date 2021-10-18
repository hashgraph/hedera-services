package com.hedera.services.state;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.ServicesState;
import com.hedera.services.context.StateChildren;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlob;
import com.hedera.services.state.merkle.MerkleContractStorageValue;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.internals.BlobKey;
import com.hedera.services.state.merkle.internals.ContractStorageKey;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.AddressBook;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;

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
		children.setRunningHashLeaf(state.runningHashLeaf());
		children.setContractStorage(state.contractStorage());
	}

	public MerkleMap<ContractStorageKey, MerkleContractStorageValue> contractStorage() {
		return children.getContractStorage();
	}

	public MerkleMap<EntityNum, MerkleAccount> accounts() {
		return children.getAccounts();
	}

	public MerkleMap<EntityNum, MerkleTopic> topics() {
		return children.getTopics();
	}

	public MerkleMap<BlobKey, MerkleBlob> storage() {
		return children.getStorage();
	}

	public MerkleMap<EntityNum, MerkleToken> tokens() {
		return children.getTokens();
	}

	public MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations() {
		return children.getTokenAssociations();
	}

	public MerkleMap<EntityNum, MerkleSchedule> schedules() {
		return children.getSchedules();
	}

	public MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens() {
		return children.getUniqueTokens();
	}

	public FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations() {
		return children.getUniqueTokenAssociations();
	}

	public FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations() {
		return children.getUniqueOwnershipAssociations();
	}

	public FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations() {
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

	public RecordsRunningHashLeaf runningHashLeaf() {
		return children.getRunningHashLeaf();
	}

	public StateChildren children() {
		return children;
	}
}
