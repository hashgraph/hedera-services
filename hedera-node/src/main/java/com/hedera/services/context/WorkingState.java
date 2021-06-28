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
import com.swirlds.common.AddressBook;
import com.swirlds.fcmap.FCMap;

/**
 * Manages the working state of the services. This gets updated by {@link ServicesContext} on a regular interval. The
 * intention of this class is to avoid making repetitive calls to get the state when we know it has not yet been updated.
 */
public class WorkingState {

	/** Working state of accounts */
	private FCMap<MerkleEntityId, MerkleAccount> accounts = new FCMap<>();

	/** Working state of topics */
	private FCMap<MerkleEntityId, MerkleTopic> topics = new FCMap<>();

	/** Working state of tokens */
	private FCMap<MerkleEntityId, MerkleToken> tokens = new FCMap<>();

	/** Working state of schedules */
	private FCMap<MerkleEntityId, MerkleSchedule> schedules = new FCMap<>();

	/** Working state of storage */
	private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage = new FCMap<>();

	/** Working state of token associations */
	private FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations = new FCMap<>();

	/** Working state of network context */
	private MerkleNetworkContext networkCtx = new MerkleNetworkContext();

	/** Working state of address book */
	private AddressBook addressBook = new AddressBook();

	/** Working state of disk fs */
	private MerkleDiskFs diskFs = new MerkleDiskFs();

	public FCMap<MerkleEntityId, MerkleAccount> getAccounts() {
		return accounts;
	}

	public void setAccounts(FCMap<MerkleEntityId, MerkleAccount> accounts) {
		this.accounts = accounts;
	}

	public FCMap<MerkleEntityId, MerkleTopic> getTopics() {
		return topics;
	}

	public void setTopics(FCMap<MerkleEntityId, MerkleTopic> topics) {
		this.topics = topics;
	}

	public FCMap<MerkleEntityId, MerkleToken> getTokens() {
		return tokens;
	}

	public void setTokens(FCMap<MerkleEntityId, MerkleToken> tokens) {
		this.tokens = tokens;
	}

	public FCMap<MerkleEntityId, MerkleSchedule> getSchedules() {
		return schedules;
	}

	public void setSchedules(
			FCMap<MerkleEntityId, MerkleSchedule> schedules) {
		this.schedules = schedules;
	}

	public FCMap<MerkleBlobMeta, MerkleOptionalBlob> getStorage() {
		return storage;
	}

	public void setStorage(
			FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage) {
		this.storage = storage;
	}

	public FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> getTokenAssociations() {
		return tokenAssociations;
	}

	public void setTokenAssociations(
			FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations) {
		this.tokenAssociations = tokenAssociations;
	}

	public MerkleNetworkContext getNetworkCtx() {
		return networkCtx;
	}

	public void setNetworkCtx(MerkleNetworkContext networkCtx) {
		this.networkCtx = networkCtx;
	}

	public AddressBook getAddressBook() {
		return addressBook;
	}

	public void setAddressBook(AddressBook addressBook) {
		this.addressBook = addressBook;
	}

	public MerkleDiskFs getDiskFs() {
		return diskFs;
	}

	public void setDiskFs(MerkleDiskFs diskFs) {
		this.diskFs = diskFs;
	}

}


