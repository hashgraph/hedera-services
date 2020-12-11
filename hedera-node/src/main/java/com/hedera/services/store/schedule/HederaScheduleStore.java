package com.hedera.services.store.schedule;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.HederaStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.swirlds.fcmap.FCMap;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityId.fromScheduleId;
import static com.hedera.services.store.CreationResult.success;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_WAS_DELETED;

/**
 * Provides a managing store for Scheduled Entities.
 *
 * @author Daniel Ivanov
 */
public class HederaScheduleStore extends HederaStore implements ScheduleStore {
	static final ScheduleID NO_PENDING_ID = ScheduleID.getDefaultInstance();

	private final Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules;

	ScheduleID pendingId = NO_PENDING_ID;
	MerkleSchedule pendingCreation;

	public HederaScheduleStore(
			EntityIdSource ids,
			Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules
	) {
		super(ids);
		this.schedules = schedules;
	}

	@Override
	public MerkleSchedule get(ScheduleID id) {
		throwIfMissing(id);

		return pendingId.equals(id) ? pendingCreation : schedules.get().get(fromScheduleId(id));
	}

	private void throwIfMissing(ScheduleID id) {
		if (!exists(id)) {
			throw new IllegalArgumentException(String.format("No such schedule '%s'!", readableId(id)));
		}
	}

	@Override
	public boolean exists(ScheduleID id) {
		return pendingId.equals(id) || schedules.get().containsKey(fromScheduleId(id));
	}

	@Override
	public void apply(ScheduleID id, Consumer<MerkleSchedule> change) {
		throwIfMissing(id);
		var key = fromScheduleId(id);
		var schedule = schedules.get().getForModify(key);
		Exception thrown = null;
		try {
			change.accept(schedule);
		} catch (Exception e) {
			thrown = e;
		}
		schedules.get().replace(key, schedule);
		if (thrown != null) {
			throw new IllegalArgumentException("Token change failed unexpectedly!", thrown);
		}
	}

	@Override
	public CreationResult<ScheduleID> createProvisionally(byte[] bodyBytes, Set<EntityId> signers, Map<EntityId, byte[]> signatures, Optional<JKey> adminKey, AccountID sponsor) {
		pendingId = ids.newScheduleId(sponsor);
		pendingCreation = new MerkleSchedule(
			bodyBytes,
			signers,
			signatures
		);
		adminKey.ifPresent(pendingCreation::setAdminKey);

		return success(pendingId);
	}

	@Override
	public ResponseCodeEnum putSignature(ScheduleID sID, AccountID aId, byte[] signature) {
		var validity = checkAccountExistence(aId);
		if (validity != OK) {
			return validity;
		}

		var id = resolve(sID);
		if (id == MISSING_SCHEDULE) {
			return INVALID_SCHEDULE_ID;
		}

		var schedule = get(id);
		if (schedule.isDeleted()) {
			return SCHEDULE_WAS_DELETED;
		}

		schedule.putSignature(EntityId.ofNullableAccountId(aId), signature);

		return OK;
	}

	@Override
	public ResponseCodeEnum delete(ScheduleID sID){
		return ScheduleStore.super.delete(sID);
	}

	@Override
	public void setHederaLedger(HederaLedger ledger) {
		super.setHederaLedger(ledger);
	}

	@Override
	public void setAccountsLedger(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		super.setAccountsLedger(accountsLedger);
	}

	@Override
	public void commitCreation() {
		throwIfNoCreationPending();

		schedules.get().put(fromScheduleId(pendingId), pendingCreation);
		resetPendingCreation();
	}

	@Override
	public void rollbackCreation() {
		throwIfNoCreationPending();
		super.rollbackCreation();
		resetPendingCreation();
	}

	@Override
	public boolean isCreationPending() {
		return pendingId != NO_PENDING_ID;
	}

	private void resetPendingCreation() {
		pendingId = NO_PENDING_ID;
		pendingCreation = null;
	}

	private void throwIfNoCreationPending() {
		if (pendingId == NO_PENDING_ID) {
			throw new IllegalStateException("No pending schedule creation!");
		}
	}
}
