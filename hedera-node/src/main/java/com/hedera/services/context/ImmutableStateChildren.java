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

import com.google.protobuf.ByteString;
import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link StateChildren} implementation for providing immutable access to the children of a
 * {@link ServicesState}. (Experience shows that making repeated, indirect calls to
 * {@link com.swirlds.common.merkle.utility.AbstractNaryMerkleInternal#getChild(int)} is
 * much more expensive, since the compiler does not seem to ever inline those calls.)
 */
public class ImmutableStateChildren implements StateChildren {
	private WeakReference<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private WeakReference<MerkleMap<EntityNum, MerkleTopic>> topics;
	private WeakReference<MerkleMap<EntityNum, MerkleToken>> tokens;
	private WeakReference<MerkleMap<EntityNumPair, MerkleUniqueToken>> uniqueTokens;
	private WeakReference<MerkleMap<EntityNum, MerkleSchedule>> schedules;
	private WeakReference<VirtualMap<VirtualBlobKey, VirtualBlobValue>> storage;
	private WeakReference<VirtualMap<ContractKey, ContractValue>> contractStorage;
	private WeakReference<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenAssociations;
	private WeakReference<MerkleNetworkContext> networkCtx;
	private WeakReference<AddressBook> addressBook;
	private WeakReference<MerkleSpecialFiles> specialFiles;
	private WeakReference<RecordsRunningHashLeaf> runningHashLeaf;
	private WeakReference<Map<ByteString, EntityNum>> aliases;
	private Instant signedAt = Instant.EPOCH;
	private ServicesState state;

	public ImmutableStateChildren(ServicesState state) {
		this.state = state;
	}

	@Override
	public Instant signedAt() {
		return signedAt;
	}

	@Override
	public MerkleMap<EntityNum, MerkleAccount> accounts() {
		final var refAccounts = accounts.get();
		Objects.requireNonNull(refAccounts);
		return refAccounts;
	}

	@Override
	public MerkleMap<EntityNum, MerkleTopic> topics() {
		return Objects.requireNonNull(topics.get());
	}

	@Override
	public MerkleMap<EntityNum, MerkleToken> tokens() {
		return Objects.requireNonNull(tokens.get());
	}

	@Override
	public VirtualMap<VirtualBlobKey, VirtualBlobValue> storage() {
		return Objects.requireNonNull(storage.get());
	}

	@Override
	public VirtualMap<ContractKey, ContractValue> contractStorage() {
		return Objects.requireNonNull(contractStorage.get());
	}

	@Override
	public MerkleMap<EntityNum, MerkleSchedule> schedules() {
		return Objects.requireNonNull(schedules.get());
	}

	@Override
	public MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations() {
		return Objects.requireNonNull(tokenAssociations.get());
	}

	@Override
	public MerkleNetworkContext networkCtx() {
		return Objects.requireNonNull(networkCtx.get());
	}

	@Override
	public AddressBook addressBook() {
		return Objects.requireNonNull(addressBook.get());
	}

	@Override
	public MerkleSpecialFiles specialFiles() {
		return Objects.requireNonNull(specialFiles.get());
	}

	@Override
	public MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens() {
		return Objects.requireNonNull(uniqueTokens.get());
	}

	@Override
	public RecordsRunningHashLeaf runningHashLeaf() {
		return Objects.requireNonNull(runningHashLeaf.get());
	}

	@Override
	public Map<ByteString, EntityNum> aliases() {
		return Objects.requireNonNull(aliases.get());
	}

	public void updatePrimitiveChildren(final Instant signingTime) {
		if (!state.isInitialized()) {
			throw new IllegalArgumentException("State children require an initialized state to update");
		}
		accounts = new WeakReference<>(this.state.accounts());
		topics = new WeakReference<>(this.state.topics());
		storage = new WeakReference<>(this.state.storage());
		contractStorage = new WeakReference<>(this.state.contractStorage());
		tokens = new WeakReference<>(this.state.tokens());
		tokenAssociations = new WeakReference<>(this.state.tokenAssociations());
		schedules = new WeakReference<>(this.state.scheduleTxs());
		networkCtx = new WeakReference<>(this.state.networkCtx());
		addressBook = new WeakReference<>(this.state.addressBook());
		specialFiles = new WeakReference<>(this.state.specialFiles());
		uniqueTokens = new WeakReference<>(this.state.uniqueTokens());
		runningHashLeaf = new WeakReference<>(this.state.runningHashLeaf());
		aliases = new WeakReference<>(this.state.aliases());
		signedAt = signingTime;
	}
}
