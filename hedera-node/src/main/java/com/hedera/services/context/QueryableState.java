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
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.swirlds.fcmap.FCMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the queryable state of the services. This gets updated by {@link ServicesContext} on a regular interval.
 */
public class QueryableState {

	/** Queryable state of topics */
	private AtomicReference<FCMap<MerkleEntityId, MerkleTopic>> queryableTopics;

	/** Queryable state of tokens */
	private AtomicReference<FCMap<MerkleEntityId, MerkleToken>> queryableTokens;

	/** Queryable state of accounts */
	private AtomicReference<FCMap<MerkleEntityId, MerkleAccount>> queryableAccounts;

	/** Queryable state of schedules */
	private AtomicReference<FCMap<MerkleEntityId, MerkleSchedule>> queryableSchedules;

	/** Queryable state of storage */
	private AtomicReference<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> queryableStorage;

	/** Queryable state of token associations */
	private AtomicReference<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> queryableTokenAssociations;

	public AtomicReference<FCMap<MerkleEntityId, MerkleTopic>> getQueryableTopics() {
		Objects.requireNonNull(queryableTopics, "A queryable state with null topics map is never valid");
		return queryableTopics;
	}

	public void setQueryableTopics(
			AtomicReference<FCMap<MerkleEntityId, MerkleTopic>> queryableTopics) {
		this.queryableTopics = queryableTopics;
	}

	public AtomicReference<FCMap<MerkleEntityId, MerkleToken>> getQueryableTokens() {
		Objects.requireNonNull(queryableTokens, "A queryable state with null tokens map is never valid");
		return queryableTokens;
	}

	public void setQueryableTokens(
			AtomicReference<FCMap<MerkleEntityId, MerkleToken>> queryableTokens) {
		this.queryableTokens = queryableTokens;
	}

	public AtomicReference<FCMap<MerkleEntityId, MerkleAccount>> getQueryableAccounts() {
		Objects.requireNonNull(queryableAccounts, "A queryable state with null accounts map is never valid");
		return queryableAccounts;
	}

	public void setQueryableAccounts(
			AtomicReference<FCMap<MerkleEntityId, MerkleAccount>> queryableAccounts) {
		this.queryableAccounts = queryableAccounts;
	}

	public AtomicReference<FCMap<MerkleEntityId, MerkleSchedule>> getQueryableSchedules() {
		Objects.requireNonNull(queryableSchedules, "A queryable state with null schedules map is never valid");
		return queryableSchedules;
	}

	public void setQueryableSchedules(
			AtomicReference<FCMap<MerkleEntityId, MerkleSchedule>> queryableSchedules) {
		this.queryableSchedules = queryableSchedules;
	}

	public AtomicReference<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> getQueryableStorage() {
		Objects.requireNonNull(queryableStorage, "A queryable state with null storage map is never valid");
		return queryableStorage;
	}

	public void setQueryableStorage(
			AtomicReference<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> queryableStorage) {
		this.queryableStorage = queryableStorage;
	}

	public AtomicReference<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> getQueryableTokenAssociations() {
		Objects.requireNonNull(queryableTokenAssociations, "A queryable state with null token associations map is never valid");
		return queryableTokenAssociations;
	}

	public void setQueryableTokenAssociations(
			AtomicReference<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> queryableTokenAssociations) {
		this.queryableTokenAssociations = queryableTokenAssociations;
	}
}


