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
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.Objects;

/**
 * A {@link StateChildren} implementation for providing cheap repeated access to the children of a
 * {@link ServicesState}. (Experience shows that making repeated, indirect calls to
 * {@link com.swirlds.common.merkle.utility.AbstractNaryMerkleInternal#getChild(int)} is
 * much more expensive, since the compiler does not seem to ever inline those calls.)
 */
public class MutableStateChildren implements StateChildren {
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
	private WeakReference<FCHashMap<ByteString, EntityNum>> aliases;
	private Instant signedAt = Instant.EPOCH;

	public MutableStateChildren() {
		/* No-op */
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

	public void setAccounts(MerkleMap<EntityNum, MerkleAccount> accounts) {
		this.accounts = new WeakReference<>(accounts);
	}

	@Override
	public MerkleMap<EntityNum, MerkleTopic> topics() {
		final var refTopics = topics.get();
		Objects.requireNonNull(refTopics);
		return refTopics;
	}

	public void setTopics(MerkleMap<EntityNum, MerkleTopic> topics) {
		this.topics = new WeakReference<>(topics);
	}

	@Override
	public MerkleMap<EntityNum, MerkleToken> tokens() {
		final var refTokens = tokens.get();
		Objects.requireNonNull(refTokens);
		return refTokens;
	}

	public void setTokens(MerkleMap<EntityNum, MerkleToken> tokens) {
		this.tokens = new WeakReference<>(tokens);
	}

	@Override
	public VirtualMap<VirtualBlobKey, VirtualBlobValue> storage() {
		final var refStorage = storage.get();
		Objects.requireNonNull(refStorage);
		return refStorage;
	}

	public void setStorage(VirtualMap<VirtualBlobKey, VirtualBlobValue> storage) {
		this.storage = new WeakReference<>(storage);
	}

	@Override
	public VirtualMap<ContractKey, ContractValue> contractStorage() {
		final var refContractStorage = contractStorage.get();
		Objects.requireNonNull(refContractStorage);
		return refContractStorage;
	}

	public void setContractStorage(VirtualMap<ContractKey, ContractValue> contractStorage) {
		this.contractStorage = new WeakReference<>(contractStorage);
	}

	@Override
	public MerkleMap<EntityNum, MerkleSchedule> schedules() {
		final var refSchedules = schedules.get();
		Objects.requireNonNull(refSchedules);
		return refSchedules;
	}

	@Override
	public MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations() {
		final var refTokenAssociations = tokenAssociations.get();
		Objects.requireNonNull(refTokenAssociations);
		return refTokenAssociations;
	}

	public void setTokenAssociations(MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations) {
		this.tokenAssociations = new WeakReference<>(tokenAssociations);
	}

	@Override
	public MerkleNetworkContext networkCtx() {
		final var refNetworkCtx = networkCtx.get();
		Objects.requireNonNull(refNetworkCtx);
		return refNetworkCtx;
	}

	@Override
	public AddressBook addressBook() {
		final var refAddressBook = addressBook.get();
		Objects.requireNonNull(refAddressBook);
		return refAddressBook;
	}

	@Override
	public MerkleSpecialFiles specialFiles() {
		final var refSpecialFiles = specialFiles.get();
		Objects.requireNonNull(refSpecialFiles);
		return refSpecialFiles;
	}

	public void setSpecialFiles(MerkleSpecialFiles specialFiles) {
		this.specialFiles = new WeakReference<>(specialFiles);
	}

	@Override
	public MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens() {
		final var refUniqueTokens = uniqueTokens.get();
		Objects.requireNonNull(refUniqueTokens);
		return refUniqueTokens;
	}

	public void setUniqueTokens(MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens) {
		this.uniqueTokens = new WeakReference<>(uniqueTokens);
	}

	@Override
	public RecordsRunningHashLeaf runningHashLeaf() {
		final var refRunningHashLeaf = runningHashLeaf.get();
		Objects.requireNonNull(refRunningHashLeaf);
		return refRunningHashLeaf;
	}

	@Override
	public FCHashMap<ByteString, EntityNum> aliases() {
		final var refAliases = aliases.get();
		Objects.requireNonNull(refAliases);
		return refAliases;
	}

	public void updateFromSigned(final ServicesState signedState, final Instant signingTime) {
		updateFrom(signedState);
		signedAt = signingTime;
	}

	public void updateFrom(final ServicesState state) {
		if (!state.isInitialized()) {
			throw new IllegalArgumentException("State children require an initialized state to update");
		}
		updatePrimitiveChildrenFrom(state);
	}

	public void updatePrimitiveChildrenFrom(final ServicesState state) {
		accounts = new WeakReference<>(state.accounts());
		topics = new WeakReference<>(state.topics());
		storage = new WeakReference<>(state.storage());
		contractStorage = new WeakReference<>(state.contractStorage());
		tokens = new WeakReference<>(state.tokens());
		tokenAssociations = new WeakReference<>(state.tokenAssociations());
		schedules = new WeakReference<>(state.scheduleTxs());
		networkCtx = new WeakReference<>(state.networkCtx());
		addressBook = new WeakReference<>(state.addressBook());
		specialFiles = new WeakReference<>(state.specialFiles());
		uniqueTokens = new WeakReference<>(state.uniqueTokens());
		runningHashLeaf = new WeakReference<>(state.runningHashLeaf());
		aliases = new WeakReference<>(state.aliases());
	}
}
