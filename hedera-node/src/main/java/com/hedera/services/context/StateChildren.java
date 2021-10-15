package com.hedera.services.context;

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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlob;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.internals.BlobKey;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.AddressBook;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;

import java.util.Objects;

/**
 * Manages the state of the services. This gets updated in {@link com.hedera.services.ServicesState} callbacks
 * on a regular interval. The intention of this class is to avoid making repetitive calls to get the state when
 * we know it has not yet been updated.
 */
public class StateChildren {
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	private MerkleMap<EntityNum, MerkleTopic> topics;
	private MerkleMap<EntityNum, MerkleToken> tokens;
	private MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens;
	private MerkleMap<EntityNum, MerkleSchedule> schedules;
	private MerkleMap<BlobKey, MerkleBlob> storage;
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations;
	private FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations;
	private FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations;
	private FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations;
	private MerkleNetworkContext networkCtx;
	private AddressBook addressBook;
	private MerkleDiskFs diskFs;
	private RecordsRunningHashLeaf runningHashLeaf;

	public MerkleMap<EntityNum, MerkleAccount> getAccounts() {
		Objects.requireNonNull(accounts);
		return accounts;
	}

	public void setAccounts(MerkleMap<EntityNum, MerkleAccount> accounts) {
		this.accounts = accounts;
	}

	public MerkleMap<EntityNum, MerkleTopic> getTopics() {
		Objects.requireNonNull(topics);
		return topics;
	}

	public void setTopics(MerkleMap<EntityNum, MerkleTopic> topics) {
		this.topics = topics;
	}

	public MerkleMap<EntityNum, MerkleToken> getTokens() {
		Objects.requireNonNull(tokens);
		return tokens;
	}

	public void setTokens(MerkleMap<EntityNum, MerkleToken> tokens) {
		this.tokens = tokens;
	}

	public MerkleMap<EntityNum, MerkleSchedule> getSchedules() {
		Objects.requireNonNull(schedules);
		return schedules;
	}

	public void setSchedules(MerkleMap<EntityNum, MerkleSchedule> schedules) {
		this.schedules = schedules;
	}

	public MerkleMap<BlobKey, MerkleBlob> getStorage() {
		Objects.requireNonNull(storage);
		return storage;
	}

	public void setStorage(MerkleMap<BlobKey, MerkleBlob> storage) {
		this.storage = storage;
	}

	public MerkleMap<EntityNumPair, MerkleTokenRelStatus> getTokenAssociations() {
		Objects.requireNonNull(tokenAssociations);
		return tokenAssociations;
	}

	public void setTokenAssociations(MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations) {
		this.tokenAssociations = tokenAssociations;
	}

	public MerkleNetworkContext getNetworkCtx() {
		Objects.requireNonNull(networkCtx);
		return networkCtx;
	}

	public void setNetworkCtx(MerkleNetworkContext networkCtx) {
		this.networkCtx = networkCtx;
	}

	public AddressBook getAddressBook() {
		Objects.requireNonNull(addressBook);
		return addressBook;
	}

	public void setAddressBook(AddressBook addressBook) {
		this.addressBook = addressBook;
	}

	public MerkleDiskFs getDiskFs() {
		Objects.requireNonNull(diskFs);
		return diskFs;
	}

	public void setDiskFs(MerkleDiskFs diskFs) {
		this.diskFs = diskFs;
	}

	public MerkleMap<EntityNumPair, MerkleUniqueToken> getUniqueTokens() {
		Objects.requireNonNull(uniqueTokens);
		return uniqueTokens;
	}

	public void setUniqueTokens(MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens) {
		this.uniqueTokens = uniqueTokens;
	}

	public FCOneToManyRelation<EntityNum, Long> getUniqueTokenAssociations() {
		Objects.requireNonNull(uniqueTokenAssociations);
		return uniqueTokenAssociations;
	}

	public void setUniqueTokenAssociations(FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations) {
		this.uniqueTokenAssociations = uniqueTokenAssociations;
	}

	public FCOneToManyRelation<EntityNum, Long> getUniqueOwnershipAssociations() {
		Objects.requireNonNull(uniqueOwnershipAssociations);
		return uniqueOwnershipAssociations;
	}

	public void setUniqueOwnershipAssociations(
			FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations
	) {
		this.uniqueOwnershipAssociations = uniqueOwnershipAssociations;
	}

	public FCOneToManyRelation<EntityNum, Long> getUniqueOwnershipTreasuryAssociations() {
		Objects.requireNonNull(uniqueOwnershipTreasuryAssociations);
		return uniqueOwnershipTreasuryAssociations;
	}

	public void setUniqueOwnershipTreasuryAssociations(
			FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations
	) {
		this.uniqueOwnershipTreasuryAssociations = uniqueOwnershipTreasuryAssociations;
	}

	public RecordsRunningHashLeaf getRunningHashLeaf() {
		Objects.requireNonNull(runningHashLeaf);
		return runningHashLeaf;
	}

	public void setRunningHashLeaf(RecordsRunningHashLeaf runningHashLeaf) {
		this.runningHashLeaf = runningHashLeaf;
	}
}


