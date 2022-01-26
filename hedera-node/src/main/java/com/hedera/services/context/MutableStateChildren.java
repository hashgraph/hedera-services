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
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;

import java.lang.ref.WeakReference;
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
	private WeakReference<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private WeakReference<MerkleMap<EntityNum, MerkleTopic>> topics;
	private WeakReference<MerkleMap<EntityNum, MerkleToken>> tokens;
	private WeakReference<MerkleMap<EntityNumPair, MerkleUniqueToken>> uniqueTokens;
	private WeakReference<MerkleMap<EntityNum, MerkleSchedule>> schedules;
	private WeakReference<VirtualMap<VirtualBlobKey, VirtualBlobValue>> storage;
	private WeakReference<VirtualMap<ContractKey, ContractValue>> contractStorage;
	private WeakReference<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenAssociations;
	private WeakReference<FCOneToManyRelation<EntityNum, Long>> uniqueTokenAssociations;
	private WeakReference<FCOneToManyRelation<EntityNum, Long>> uniqueOwnershipAssociations;
	private WeakReference<FCOneToManyRelation<EntityNum, Long>> uniqueOwnershipTreasuryAssociations;
	private WeakReference<MerkleNetworkContext> networkCtx;
	private WeakReference<AddressBook> addressBook;
	private WeakReference<MerkleSpecialFiles> specialFiles;
	private WeakReference<RecordsRunningHashLeaf> runningHashLeaf;
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

	public void setSchedules(MerkleMap<EntityNum, MerkleSchedule> schedules) {
		this.schedules = new WeakReference<>(schedules);
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

	public void setNetworkCtx(MerkleNetworkContext networkCtx) {
		this.networkCtx = new WeakReference<>(networkCtx);
	}

	@Override
	public AddressBook addressBook() {
		final var refAddressBook = addressBook.get();
		Objects.requireNonNull(refAddressBook);
		return refAddressBook;
	}

	public void setAddressBook(AddressBook addressBook) {
		this.addressBook = new WeakReference<>(addressBook);
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
	public FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations() {
		final var refUniqueTokenAssociations = uniqueTokenAssociations.get();
		Objects.requireNonNull(refUniqueTokenAssociations);
		return refUniqueTokenAssociations;
	}

	public void setUniqueTokenAssociations(FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations) {
		this.uniqueTokenAssociations = new WeakReference<>(uniqueTokenAssociations);
	}

	@Override
	public FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations() {
		final var refUniqueOwnershipAssociations = uniqueOwnershipAssociations.get();
		Objects.requireNonNull(refUniqueOwnershipAssociations);
		return refUniqueOwnershipAssociations;
	}

	public void setUniqueOwnershipAssociations(
			FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations
	) {
		this.uniqueOwnershipAssociations = new WeakReference<>(uniqueOwnershipAssociations);
	}

	@Override
	public FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations() {
		final var refUniqueOwnershipTreasuryAssociations = uniqueOwnershipTreasuryAssociations.get();
		Objects.requireNonNull(refUniqueOwnershipTreasuryAssociations);
		return refUniqueOwnershipTreasuryAssociations;
	}

	public void setUniqueOwnershipTreasuryAssociations(
			FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations
	) {
		this.uniqueOwnershipTreasuryAssociations = new WeakReference<>(uniqueOwnershipTreasuryAssociations);
	}

	@Override
	public RecordsRunningHashLeaf runningHashLeaf() {
		final var refRunningHashLeaf = runningHashLeaf.get();
		Objects.requireNonNull(refRunningHashLeaf);
		return refRunningHashLeaf;
	}


	public void setRunningHashLeaf(RecordsRunningHashLeaf runningHashLeaf) {
		this.runningHashLeaf = new WeakReference<>(runningHashLeaf);
	}

	public void updateFromMaybeUninitializedState(final ServicesState signedState, final Instant signingTime) {
		signedAt = signingTime;
		updatePrimitiveChildrenFrom(signedState);
		uniqueTokenAssociations = new WeakReference<>(null);
		uniqueOwnershipAssociations = new WeakReference<>(null);
		uniqueOwnershipTreasuryAssociations = new WeakReference<>(null);
	}

	public void updateFrom(final ServicesState state) {
		updatePrimitiveChildrenFrom(state);
		uniqueTokenAssociations = new WeakReference<>(state.uniqueTokenAssociations());
		uniqueOwnershipAssociations = new WeakReference<>(state.uniqueOwnershipAssociations());
		uniqueOwnershipTreasuryAssociations = new WeakReference<>(state.uniqueTreasuryOwnershipAssociations());
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
	}
}
