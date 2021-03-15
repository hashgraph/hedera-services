package com.hedera.services.store.schedule;

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

import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.Store;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Defines a type able to manage Scheduled entities.
 *
 * @author Daniel Ivanov
 * @author Michael Tinker
 */
public interface ScheduleStore extends Store<ScheduleID, MerkleSchedule> {
	ScheduleID MISSING_SCHEDULE = ScheduleID.getDefaultInstance();

	void apply(ScheduleID id, Consumer<MerkleSchedule> change);
	ResponseCodeEnum delete(ScheduleID id);

	CreationResult<ScheduleID> createProvisionally(MerkleSchedule candidate, RichInstant consensusTime);

	Pair<Optional<ScheduleID>, MerkleSchedule> lookupSchedule(byte[] bodyBytes);
	ResponseCodeEnum markAsExecuted(ScheduleID id);
	void expire(EntityId id);

	default ScheduleID resolve(ScheduleID id) {
		return exists(id) ? id : MISSING_SCHEDULE;
	}
}
