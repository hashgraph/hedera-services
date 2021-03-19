package com.hedera.services.store.schedule;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.HederaStore;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityId.fromScheduleId;
import static com.hedera.services.store.CreationResult.failure;
import static com.hedera.services.store.CreationResult.success;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;

/**
 * Provides a managing store for Scheduled Entities.
 *
 * @author Daniel Ivanov
 * @author Michael Tinker
 */
public class HederaScheduleStore extends HederaStore implements ScheduleStore {
	static final ScheduleID NO_PENDING_ID = ScheduleID.getDefaultInstance();

	private final GlobalDynamicProperties properties;
	private final Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules;

	ScheduleID pendingId = NO_PENDING_ID;
	MerkleSchedule pendingCreation;
	TransactionContext txnCtx;
	Map<MerkleSchedule, MerkleEntityId> extantSchedules = new HashMap<>();

	public HederaScheduleStore(
			GlobalDynamicProperties properties,
			EntityIdSource ids,
			TransactionContext txnCtx,
			Supplier<FCMap<MerkleEntityId, MerkleSchedule>> schedules
	) {
		super(ids);
		this.txnCtx = txnCtx;
		this.schedules = schedules;
		this.properties = properties;
		buildContentAddressableViewOfExtantSchedules();
	}

	@Override
	public MerkleSchedule get(ScheduleID id) {
		throwIfMissing(id);

		return pendingId.equals(id) ? pendingCreation : schedules.get().get(fromScheduleId(id));
	}

	@Override
	public boolean exists(ScheduleID id) {
		return (isCreationPending() && pendingId.equals(id)) || schedules.get().containsKey(fromScheduleId(id));
	}

	@Override
	public void apply(ScheduleID id, Consumer<MerkleSchedule> change) {
		throwIfMissing(id);

		if (id.equals(pendingId)) {
			applyProvisionally(change);
			return;
		}

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

	private void applyProvisionally(Consumer<MerkleSchedule> change) {
		change.accept(pendingCreation);
	}

	@Override
	public CreationResult<ScheduleID> createProvisionally(MerkleSchedule schedule, RichInstant consensusTime) {
		schedule.setExpiry(consensusTime.getSeconds() + properties.scheduledTxExpiryTimeSecs());

		var validity = OK;
		if (schedule.hasExplicitPayer()) {
			validity = accountCheck(schedule.payer().toGrpcAccountId(), INVALID_SCHEDULE_PAYER_ID);
		}
		if (validity == OK) {
			validity = accountCheck(schedule.schedulingAccount().toGrpcAccountId(), INVALID_SCHEDULE_ACCOUNT_ID);
		}
		if (validity != OK) {
			return failure(validity);
		}

		pendingId = ids.newScheduleId(schedule.schedulingAccount().toGrpcAccountId());
		pendingCreation = schedule;

		return success(pendingId);
	}

	@Override
	public ResponseCodeEnum delete(ScheduleID id) {
		var status = usabilityCheck(id, true);
		if (status != OK) {
			return status;
		}

		apply(id, schedule -> schedule.markDeleted(txnCtx.consensusTime()));
		return OK;
	}

	@Override
	public void commitCreation() {
		throwIfNoCreationPending();

		var id = fromScheduleId(pendingId);
		schedules.get().put(id, pendingCreation);
		extantSchedules.put(pendingCreation.toContentAddressableView(), id);
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

	private void buildContentAddressableViewOfExtantSchedules() {
		schedules.get().forEach((key, value) -> extantSchedules.put(value.toContentAddressableView(), key));
	}

	@Override
	public void rebuildViews() {
		extantSchedules.clear();
		buildContentAddressableViewOfExtantSchedules();
	}

	@Override
	public Pair<Optional<ScheduleID>, MerkleSchedule> lookupSchedule(byte[] bodyBytes) {
		var schedule = MerkleSchedule.from(bodyBytes, 0L);

		if (isCreationPending()) {
			if (schedule.equals(pendingCreation)) {
				return Pair.of(Optional.of(pendingId), pendingCreation);
			}
		}
		if (extantSchedules.containsKey(schedule)) {
			var extantId = extantSchedules.get(schedule);
			return Pair.of(Optional.of(extantId.toScheduleId()), schedules.get().get(extantId));
		}

		return Pair.of(Optional.empty(), schedule);
	}

	@Override
	public ResponseCodeEnum markAsExecuted(ScheduleID id) {
		var status = usabilityCheck(id, false);
		if (status != OK) {
			return status;
		}
		apply(id, schedule -> schedule.markExecuted(txnCtx.consensusTime().plusNanos(1L)));
		return OK;
	}

	@Override
	public void expire(EntityId entityId) {
		var id = entityId.toGrpcScheduleId();
		if (id.equals(pendingId)) {
			throw new IllegalArgumentException(String.format(
					"Argument 'id=%s' refers to a pending creation!",
					readableId(id)));
		}
		var schedule = get(id);
		schedules.get().remove(entityId.asMerkle());
		extantSchedules.remove(schedule);
	}

	public Map<MerkleSchedule, MerkleEntityId> getExtantSchedules() {
		return extantSchedules;
	}

	private ResponseCodeEnum usabilityCheck(
			ScheduleID id,
			boolean requiresMutability
	) {
		var idRes = resolve(id);
		if (idRes == MISSING_SCHEDULE) {
			return INVALID_SCHEDULE_ID;
		}

		var schedule = get(id);
		if (schedule.isDeleted()) {
			return SCHEDULE_ALREADY_DELETED;
		}
		if (schedule.isExecuted()) {
			return SCHEDULE_ALREADY_EXECUTED;
		}
		if (requiresMutability) {
			if (schedule.adminKey().isEmpty()) {
				return SCHEDULE_IS_IMMUTABLE;
			}
		}

		return OK;
	}

	private void throwIfMissing(ScheduleID id) {
		if (!exists(id)) {
			throw new IllegalArgumentException(String.format(
					"Argument 'id=%s' does not refer to an extant scheduled entity!",
					readableId(id)));
		}
	}
}
