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
import java.util.Objects;

/**
 * A {@link StateChildren} implementation appropriate for providing quick access to the children of the working state,
 * which are constantly changing.
 *
 * <b>Not</b> thread-safe, so ideally will only be used inside the synchronized {@link com.hedera.services.ServicesState}
 * methods {@code init()}, {@code copy()}, and {@code handleTransaction()}.
 */
public class MutableStateChildren implements StateChildren {
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	private MerkleMap<EntityNum, MerkleTopic> topics;
	private MerkleMap<EntityNum, MerkleToken> tokens;
	private MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens;
	private MerkleMap<EntityNum, MerkleSchedule> schedules;
	private MerkleMap<String, MerkleOptionalBlob> storage;
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations;
	private FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations;
	private FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations;
	private FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations;
	private MerkleNetworkContext networkCtx;
	private AddressBook addressBook;
	private MerkleSpecialFiles specialFiles;
	private RecordsRunningHashLeaf runningHashLeaf;
	private Instant signedAt = Instant.EPOCH;

	public MutableStateChildren() {
		/* No-op */
	}

	public MutableStateChildren(final Instant signedAt) {
		this.signedAt = signedAt;
	}

	@Override
	public Instant signedAt() {
		return signedAt;
	}

	@Override
	public MerkleMap<EntityNum, MerkleAccount> accounts() {
		Objects.requireNonNull(accounts);
		return accounts;
	}

	public void setAccounts(MerkleMap<EntityNum, MerkleAccount> accounts) {
		this.accounts = accounts;
	}

	@Override
	public MerkleMap<EntityNum, MerkleTopic> topics() {
		Objects.requireNonNull(topics);
		return topics;
	}

	public void setTopics(MerkleMap<EntityNum, MerkleTopic> topics) {
		this.topics = topics;
	}

	@Override
	public MerkleMap<EntityNum, MerkleToken> tokens() {
		Objects.requireNonNull(tokens);
		return tokens;
	}

	public void setTokens(MerkleMap<EntityNum, MerkleToken> tokens) {
		this.tokens = tokens;
	}

	@Override
	public MerkleMap<EntityNum, MerkleSchedule> schedules() {
		Objects.requireNonNull(schedules);
		return schedules;
	}

	public void setSchedules(MerkleMap<EntityNum, MerkleSchedule> schedules) {
		this.schedules = schedules;
	}

	@Override
	public MerkleMap<String, MerkleOptionalBlob> storage() {
		Objects.requireNonNull(storage);
		return storage;
	}

	public void setStorage(MerkleMap<String, MerkleOptionalBlob> storage) {
		this.storage = storage;
	}

	@Override
	public MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations() {
		Objects.requireNonNull(tokenAssociations);
		return tokenAssociations;
	}

	public void setTokenAssociations(MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations) {
		this.tokenAssociations = tokenAssociations;
	}

	@Override
	public MerkleNetworkContext networkCtx() {
		Objects.requireNonNull(networkCtx);
		return networkCtx;
	}

	public void setNetworkCtx(MerkleNetworkContext networkCtx) {
		this.networkCtx = networkCtx;
	}

	@Override
	public AddressBook addressBook() {
		Objects.requireNonNull(addressBook);
		return addressBook;
	}

	public void setAddressBook(AddressBook addressBook) {
		this.addressBook = addressBook;
	}

	@Override
	public MerkleSpecialFiles specialFiles() {
		Objects.requireNonNull(specialFiles);
		return specialFiles;
	}

	public void setSpecialFiles(MerkleSpecialFiles specialFiles) {
		this.specialFiles = specialFiles;
	}

	@Override
	public MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens() {
		Objects.requireNonNull(uniqueTokens);
		return uniqueTokens;
	}

	public void setUniqueTokens(MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens) {
		this.uniqueTokens = uniqueTokens;
	}

	@Override
	public FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations() {
		Objects.requireNonNull(uniqueTokenAssociations);
		return uniqueTokenAssociations;
	}

	public void setUniqueTokenAssociations(FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations) {
		this.uniqueTokenAssociations = uniqueTokenAssociations;
	}

	@Override
	public FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations() {
		Objects.requireNonNull(uniqueOwnershipAssociations);
		return uniqueOwnershipAssociations;
	}

	public void setUniqueOwnershipAssociations(
			FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations
	) {
		this.uniqueOwnershipAssociations = uniqueOwnershipAssociations;
	}

	@Override
	public FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations() {
		Objects.requireNonNull(uniqueOwnershipTreasuryAssociations);
		return uniqueOwnershipTreasuryAssociations;
	}

	public void setUniqueOwnershipTreasuryAssociations(
			FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations
	) {
		this.uniqueOwnershipTreasuryAssociations = uniqueOwnershipTreasuryAssociations;
	}

	@Override
	public RecordsRunningHashLeaf runningHashLeaf() {
		Objects.requireNonNull(runningHashLeaf);
		return runningHashLeaf;
	}

	public void setRunningHashLeaf(RecordsRunningHashLeaf runningHashLeaf) {
		this.runningHashLeaf = runningHashLeaf;
	}
}


