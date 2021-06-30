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
import java.util.Objects;

/**
 * Manages the working state of the services. This gets updated by {@link ServicesContext} on a regular interval. The
 * intention of this class is to avoid making repetitive calls to get the state when we know it has not yet been updated.
 */
public class WorkingState {

	/** Working state of accounts */
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	/** Working state of topics */
	private FCMap<MerkleEntityId, MerkleTopic> topics;

	/** Working state of tokens */
	private FCMap<MerkleEntityId, MerkleToken> tokens;

	/** Working state of schedules */
	private FCMap<MerkleEntityId, MerkleSchedule> schedules;

	/** Working state of storage */
	private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage;

	/** Working state of token associations */
	private FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations;

	/** Working state of network context */
	private MerkleNetworkContext networkCtx;

	/** Working state of address book */
	private AddressBook addressBook;

	/** Working state of disk fs */
	private MerkleDiskFs diskFs;

	public FCMap<MerkleEntityId, MerkleAccount> getAccounts() {
		Objects.requireNonNull(accounts, "A working state with null accounts map is never valid");
		return accounts;
	}

	public void setAccounts(FCMap<MerkleEntityId, MerkleAccount> accounts) {
		this.accounts = accounts;
	}

	public FCMap<MerkleEntityId, MerkleTopic> getTopics() {
		Objects.requireNonNull(topics, "A working state with null topics map is never valid");
		return topics;
	}

	public void setTopics(FCMap<MerkleEntityId, MerkleTopic> topics) {
		this.topics = topics;
	}

	public FCMap<MerkleEntityId, MerkleToken> getTokens() {
		Objects.requireNonNull(tokens, "A working state with null tokens map is never valid");
		return tokens;
	}

	public void setTokens(FCMap<MerkleEntityId, MerkleToken> tokens) {
		this.tokens = tokens;
	}

	public FCMap<MerkleEntityId, MerkleSchedule> getSchedules() {
		Objects.requireNonNull(schedules, "A working state with null schedules map is never valid");
		return schedules;
	}

	public void setSchedules(
			FCMap<MerkleEntityId, MerkleSchedule> schedules) {
		this.schedules = schedules;
	}

	public FCMap<MerkleBlobMeta, MerkleOptionalBlob> getStorage() {
		Objects.requireNonNull(storage, "A working state with null storage map is never valid");
		return storage;
	}

	public void setStorage(
			FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage) {
		this.storage = storage;
	}

	public FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> getTokenAssociations() {
		Objects.requireNonNull(tokenAssociations, "A working state with null token associations map is never valid");
		return tokenAssociations;
	}

	public void setTokenAssociations(
			FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations) {
		this.tokenAssociations = tokenAssociations;
	}

	public MerkleNetworkContext getNetworkCtx() {
		Objects.requireNonNull(networkCtx, "A working state with null network ctx map is never valid");
		return networkCtx;
	}

	public void setNetworkCtx(MerkleNetworkContext networkCtx) {
		this.networkCtx = networkCtx;
	}

	public AddressBook getAddressBook() {
		Objects.requireNonNull(addressBook, "A working state with null address book map is never valid");
		return addressBook;
	}

	public void setAddressBook(AddressBook addressBook) {
		this.addressBook = addressBook;
	}

	public MerkleDiskFs getDiskFs() {
		Objects.requireNonNull(diskFs, "A working state with null disk fs map is never valid");
		return diskFs;
	}

	public void setDiskFs(MerkleDiskFs diskFs) {
		this.diskFs = diskFs;
	}

}


