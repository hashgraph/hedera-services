package com.hedera.services.schedule;

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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;

public enum ExceptionalScheduleStore implements ScheduleStore {
	NOOP_SCHEDULE_STORE;

	@Override
	public MerkleSchedule get(ScheduleID sID) { throw new UnsupportedOperationException(); }

	@Override
	public boolean exists(ScheduleID id) { throw new UnsupportedOperationException(); }

	@Override
	public ScheduleCreationResult createProvisionally(byte[] bodyBytes, JKey adminKey, JKey signKey, AccountID sponsor) { throw new UnsupportedOperationException(); }

	@Override
	public ResponseCodeEnum addSignature(ScheduleID sID, SignatureMap signatures) { throw new UnsupportedOperationException(); }

	@Override
	public ResponseCodeEnum delete(ScheduleID sID) { throw new UnsupportedOperationException(); }

	@Override
	public void commitCreation() { throw new UnsupportedOperationException(); }

	@Override
	public void rollbackCreation() { throw new UnsupportedOperationException(); }

	@Override
	public boolean isCreationPending() { throw new UnsupportedOperationException(); }
}
