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

import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.HederaStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.swirlds.fcmap.FCMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityId.fromScheduleId;
import static com.hedera.services.store.CreationResult.failure;
import static com.hedera.services.store.CreationResult.success;
import static com.hedera.services.store.schedule.CompositeKey.fromMerkleSchedule;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_WAS_DELETED;

/**
 * Provides a managing store for Scheduled Entities.
 *
 * @author Daniel Ivanov
 */
public class HederaScheduleStore extends HederaStore implements ScheduleStore {
	static final ScheduleID NO_PENDING_ID = ScheduleID.getDefaultInstance();

	private final Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules;
	Map<CompositeKey, MerkleEntityId> txToEntityId = new HashMap<>();

	ScheduleID pendingId = NO_PENDING_ID;
	MerkleSchedule pendingCreation;

	public HederaScheduleStore(
			EntityIdSource ids,
			Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules
	) {
		super(ids);
		this.schedules = schedules;
		buildTxToEntityIdMap(this.schedules);
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
		try {
			change.accept(schedule);
		} catch (Exception e) {
			throw new IllegalArgumentException("Schedule change failed unexpectedly!", e);
		} finally {
			schedules.get().replace(key, schedule);
		}
	}

	@Override
	public CreationResult<ScheduleID> createProvisionally(byte[] bodyBytes, AccountID payer, AccountID schedulingAccount, RichInstant schedulingTXValidStart, Optional<JKey> adminKey, Optional<String> entityMemo) {
		var validity = accountCheck(schedulingAccount, INVALID_SCHEDULE_ACCOUNT_ID);
		if (validity != OK) {
			return failure(validity);
		}
		validity = accountCheck(payer, INVALID_SCHEDULE_PAYER_ID);
		if (validity != OK) {
			return failure(validity);
		}

		pendingId = ids.newScheduleId(schedulingAccount);
		pendingCreation = new MerkleSchedule(
				bodyBytes,
				EntityId.ofNullableAccountId(schedulingAccount),
				schedulingTXValidStart);
		adminKey.ifPresent(pendingCreation::setAdminKey);
		entityMemo.ifPresent(pendingCreation::setMemo);
		pendingCreation.setPayer(EntityId.ofNullableAccountId(payer));

		return success(pendingId);
	}

	@Override
	public ResponseCodeEnum addSigners(ScheduleID sID, Set<JKey> signers) {
		var id = resolve(sID);
		if (id == MISSING_SCHEDULE) {
			return INVALID_SCHEDULE_ID;
		}

		var schedule = get(id);
		if (schedule.isDeleted()) {
			return SCHEDULE_WAS_DELETED;
		}

		schedule.addSigners(signers);

		return OK;
	}

	@Override
	public ResponseCodeEnum delete(ScheduleID id){
		var idRes = resolve(id);
		if (idRes == MISSING_SCHEDULE) {
			return INVALID_SCHEDULE_ID;
		}

		var schedule = get(id);
		if (schedule.adminKey().isEmpty()) {
			return SCHEDULE_IS_IMMUTABLE;
		}
		if (schedule.isDeleted()) {
			return SCHEDULE_WAS_DELETED;
		}

		delete(id, schedule);
		return OK;
	}

	@Override
	public void commitCreation() {
		throwIfNoCreationPending();
		var id = fromScheduleId(pendingId);

		schedules.get().put(id, pendingCreation);
		var key = fromMerkleSchedule(pendingCreation);
		txToEntityId.put(key, id);
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

	private void buildTxToEntityIdMap(Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules) {
		var schedulesMap = schedules.get();
		schedulesMap.forEach((key, value) -> txToEntityId.put(fromMerkleSchedule(value), key));
	}

	@Override
	public Optional<ScheduleID> lookupScheduleId(byte[] bodyBytes, AccountID scheduledTxPayer) {
		var keyToCheckFor = new CompositeKey(Arrays.hashCode(bodyBytes), scheduledTxPayer);

		if (isCreationPending()) {
			var pendingKey = fromMerkleSchedule(pendingCreation);

			if (keyToCheckFor.equals(pendingKey)) {
				return Optional.of(pendingId);
			}
		}

		if (txToEntityId.containsKey(keyToCheckFor)) {
			var scheduleId = txToEntityId.get(keyToCheckFor).toScheduleId();
			return Optional.of(scheduleId);
		}

		return Optional.empty();
	}

	@Override
	public ResponseCodeEnum markAsExecuted(ScheduleID id) {
		var idRes = resolve(id);
		if (idRes == MISSING_SCHEDULE) {
			return INVALID_SCHEDULE_ID;
		}

		var schedule = get(id);
		if (schedule.isDeleted()) {
			return SCHEDULE_WAS_DELETED;
		}

		delete(id, schedule);
		return OK;
	}

	private void delete(ScheduleID id, MerkleSchedule schedule) {
		apply(id, DELETION);
		txToEntityId.remove(fromMerkleSchedule(schedule));
	}
}
