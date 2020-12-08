package com.hedera.services.schedule;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_WAS_DELETED;

/**
 * Defines a type able to manage Scheduled entities.
 *
 * @author Daniel Ivanov
 */
public interface ScheduleStore {
	ScheduleID MISSING_SCHEDULE = ScheduleID.getDefaultInstance();

	MerkleSchedule get(ScheduleID sID);
	boolean exists (ScheduleID id);

	ScheduleCreationResult createProvisionally(byte[] bodyBytes, JKey adminKey, JKey signKey, AccountID sponsor);
	ResponseCodeEnum addSignature(ScheduleID sID, SignatureMap signatures);

	void commitCreation();
	void rollbackCreation();
	boolean isCreationPending();

	default ScheduleID resolve(ScheduleID id) {
		return exists(id) ? id : MISSING_SCHEDULE;
	}

	default ResponseCodeEnum delete(ScheduleID id) {
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

		// TODO verify the deletion
//		apply(id, DELETION);
		return OK;
	}

}
