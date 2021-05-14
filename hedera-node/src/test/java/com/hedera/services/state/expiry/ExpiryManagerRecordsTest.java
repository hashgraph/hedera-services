package com.hedera.services.state.expiry;

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

import com.hedera.services.records.RecordCache;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiryManagerRecordsTest {
	private final long aExpiry = 1_234_567L;
	private final long bExpiry = 1_234_568L;
	private final AccountID a = IdUtils.asAccount("0.0.2");
	private final AccountID b = IdUtils.asAccount("0.0.3");
	private final MerkleAccount aAccount = MerkleAccountFactory.newAccount().get();

	private ExpirableTxnRecord record;

	@Mock
	private RecordCache recordCache;
	@Mock
	private TxnIdRecentHistory history;
	@Mock
	private ScheduleStore scheduleStore;
	@Mock
	private Map<TransactionID, TxnIdRecentHistory> txnHistories;
	@Mock
	private FCMap<MerkleEntityId, MerkleSchedule> schedules;
	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	@Mock
	private FCQueue<ExpirableTxnRecord> records;

	private ExpiryManager subject;

	@BeforeEach
	void setUp() {
		subject = new ExpiryManager(recordCache, txnHistories, scheduleStore, () -> accounts, () -> schedules);
	}

	@Test
	void name() {
		setupModifiableAccount(a, aAccount);
		setupMockRecord(aExpiry);
		setupMockHistoryForRecord();

		subject.trackRecord(a, aExpiry);
		subject.trackRecord(b, bExpiry);

		// when:
		subject.purgeExpiredRecordsAt(aExpiry);

		// then:
		verify(records, times(1)).poll();
		verify(history).forgetExpiredAt(aExpiry);
		verify(txnHistories).remove(record.getTxnId().toGrpc());
	}

	private void setupModifiableAccount(AccountID id, MerkleAccount aAccount) {
		given(accounts.getForModify(MerkleEntityId.fromAccountId(id))).willReturn(aAccount);
		aAccount.setRecords(records);
	}

	private void setupMockHistoryForRecord() {
		final var grpcId = record.getTxnId().toGrpc();
		given(txnHistories.get(grpcId)).willReturn(history);
		given(history.isForgotten()).willReturn(true);
	}

	private void setupMockRecord(long expiry) {
		final var grpcId = TransactionID.newBuilder()
				.setAccountID(a)
				.setTransactionValidStart(MiscUtils.asTimestamp(Instant.now()))
				.build();
		record = new ExpirableTxnRecord(null, null, TxnId.fromGrpc(grpcId), null, null, 0L, null, null, null);
		record.setExpiry(expiry);
		given(records.isEmpty()).willReturn(false).willReturn(true);
		given(records.peek()).willReturn(record);
		given(records.poll()).willReturn(record);
	}
}