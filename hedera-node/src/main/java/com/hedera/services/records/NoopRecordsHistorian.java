package com.hedera.services.records;

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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stream.RecordStreamObject;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public enum NoopRecordsHistorian implements AccountRecordsHistorian {
	NOOP_RECORDS_HISTORIAN;

	@Override
	public void clearHistory() {
		/* No-op */
	}

	@Override
	public void setCreator(EntityCreator creator) {
		/* No-op */
	}

	@Override
	public void saveExpirableTransactionRecords() {
		/* No-op */
	}

	@Override
	public ExpirableTxnRecord lastCreatedTopLevelRecord() {
		return null;
	}

	@Override
	public boolean hasFollowingChildRecords() {
		return false;
	}

	@Override
	public boolean hasPrecedingChildRecords() {
		return false;
	}

	@Override
	public List<RecordStreamObject> getFollowingChildRecords() {
		return Collections.emptyList();
	}

	@Override
	public List<RecordStreamObject> getPrecedingChildRecords() {
		return Collections.emptyList();
	}

	@Override
	public int nextChildRecordSourceId() {
		return 0;
	}

	@Override
	public void trackFollowingChildRecord(
			int sourceId, TransactionBody.Builder syntheticBody, ExpirableTxnRecord.Builder recordSoFar) {
		/* No-op */
	}

	@Override
	public void trackPrecedingChildRecord(
			int sourceId, TransactionBody.Builder syntheticBody, ExpirableTxnRecord.Builder recordSoFar) {
		/* No-op */
	}

	@Override
	public void revertChildRecordsFromSource(int sourceId) {
		/* No-op */
	}

	@Override
	public void noteNewExpirationEvents() {
		/* No-op */
	}

	@Override
	public Instant nextFollowingChildConsensusTime() {
		return Instant.EPOCH;
	}

	@Override
	public JKey getActivePayerKeyFromTxnCtx() {
		return null;
	}

	@Override
	public Instant getConsensusTimeFromTxnCtx() {
		return null;
	}
}
