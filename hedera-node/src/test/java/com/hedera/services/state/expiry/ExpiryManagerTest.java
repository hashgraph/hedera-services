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

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.config.MockHederaNumbers;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpiryManagerTest {
	private static final long now = 1_234_567L;
	private static final long start = now - 180L;
	private static final long firstThen = now - 1;
	private static final long secondThen = now + 1;
	private static final AccountID aGrpcId = IdUtils.asAccount("0.0.2");
	private static final AccountID bGrpcId = IdUtils.asAccount("0.0.4");
	private static final HederaNumbers nums = new MockHederaNumbers();

	private final MerkleEntityId aKey = MerkleEntityId.fromAccountId(aGrpcId);
	private final MerkleEntityId bKey = MerkleEntityId.fromAccountId(bGrpcId);
	private final MerkleAccount anAccount = new MerkleAccount();

	private final FCMap<MerkleEntityId, MerkleAccount> liveAccounts = new FCMap<>();
	private final FCMap<MerkleEntityId, MerkleSchedule> liveSchedules = new FCMap<>();
	private final Map<TransactionID, TxnIdRecentHistory> liveTxnHistories = new HashMap<>();

	@Mock
	private ScheduleStore mockScheduleStore;
	@Mock
	private Map<TransactionID, TxnIdRecentHistory> mockTxnHistories;
	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> mockAccounts;
	@Mock
	private FCMap<MerkleEntityId, MerkleSchedule> mockSchedules;

	private ExpiryManager subject;

	@Test
	void rebuildsExpectedSchedulesFromState() {
		subject = new ExpiryManager(
				mockScheduleStore, nums, mockTxnHistories, () -> mockAccounts, () -> liveSchedules);
		final var aSchedule = new MerkleSchedule();
		final var bSchedule = new MerkleSchedule();
		aSchedule.setExpiry(firstThen);
		bSchedule.setExpiry(secondThen);
		liveSchedules.put(aKey, aSchedule);
		liveSchedules.put(bKey, bSchedule);

		subject.reviewExistingShortLivedEntities();
		final var resultingExpiries = subject.getShortLivedEntityExpiries();
		final var firstExpiry = resultingExpiries.expireNextAt(now);

		assertEquals(aKey.getNum(), firstExpiry.getLeft());
		assertEquals(1, resultingExpiries.getAllExpiries().size());
	}

	@Test
	void expiresSchedulesAsExpected() {
		subject = new ExpiryManager(
				mockScheduleStore, nums, mockTxnHistories, () -> mockAccounts, () -> mockSchedules);
		subject.trackExpirationEvent(Pair.of(aKey.getNum(), entityId -> mockScheduleStore.expire(entityId)), firstThen);
		subject.trackExpirationEvent(Pair.of(bKey.getNum(), entityId -> mockScheduleStore.expire(entityId)),
				secondThen);

		subject.purge(now);

		verify(mockScheduleStore).expire(new EntityId(0, 0, aKey.getNum()));
		assertEquals(1, subject.getShortLivedEntityExpiries().getAllExpiries().size());
	}

	@Test
	void rebuildsExpectedRecordsFromState() {
		subject = new ExpiryManager(
				mockScheduleStore, nums, liveTxnHistories, () -> liveAccounts, () -> mockSchedules);
		final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
		final var leftoverTxnId = recordWith(bGrpcId, now).getTxnId().toGrpc();
		liveTxnHistories.put(leftoverTxnId, new TxnIdRecentHistory());
		anAccount.records().offer(expiring(recordWith(aGrpcId, start), firstThen));
		anAccount.records().offer(expiring(recordWith(aGrpcId, start), secondThen));
		liveAccounts.put(aKey, anAccount);

		subject.reviewExistingPayerRecords();

		assertFalse(liveTxnHistories.containsKey(leftoverTxnId));
		assertEquals(firstThen, liveTxnHistories.get(newTxnId).priorityRecord().getExpiry());
		assertEquals(secondThen, liveTxnHistories.get(newTxnId).duplicateRecords().get(0).getExpiry());
	}

	@Test
	void expiresRecordsAsExpected() {
		subject = new ExpiryManager(
				mockScheduleStore, nums, liveTxnHistories, () -> liveAccounts, () -> mockSchedules);
		final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
		liveAccounts.put(aKey, anAccount);

		final var firstRecord = expiring(recordWith(aGrpcId, start), firstThen);
		addLiveRecord(aKey, firstRecord);
		liveTxnHistories.computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory()).observe(firstRecord, OK);
		subject.trackRecordInState(aGrpcId, firstThen);

		final var secondRecord = expiring(recordWith(aGrpcId, start), secondThen);
		addLiveRecord(aKey, secondRecord);
		liveTxnHistories.computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory()).observe(secondRecord, OK);
		subject.trackRecordInState(aGrpcId, secondThen);

		subject.purge(now);

		assertEquals(1, liveAccounts.get(aKey).records().size());
		assertEquals(secondThen, liveTxnHistories.get(newTxnId).priorityRecord().getExpiry());
	}

	@Test
	void expiresLoneRecordAsExpected() {
		subject = new ExpiryManager(
				mockScheduleStore, nums, liveTxnHistories, () -> liveAccounts, () -> mockSchedules);
		final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
		liveAccounts.put(aKey, anAccount);

		final var firstRecord = expiring(recordWith(aGrpcId, start), firstThen);
		addLiveRecord(aKey, firstRecord);
		liveTxnHistories.computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory()).observe(firstRecord, OK);
		subject.trackRecordInState(aGrpcId, firstThen);

		subject.purge(now);

		assertEquals(0, liveAccounts.get(aKey).records().size());
		assertFalse(liveTxnHistories.containsKey(newTxnId));
	}

	private void addLiveRecord(final MerkleEntityId key, final ExpirableTxnRecord expirableTxnRecord) {
		final var mutableAccount = liveAccounts.getForModify(key);
		mutableAccount.records().offer(expirableTxnRecord);
		liveAccounts.replace(aKey, mutableAccount);
	}

	private ExpirableTxnRecord expiring(final ExpirableTxnRecord expirableTxnRecord, final long at) {
		final var ans = expirableTxnRecord;
		ans.setExpiry(at);
		ans.setSubmittingMember(0L);
		return ans;
	}

	private static final ExpirableTxnRecord recordWith(final AccountID payer, final long validStartSecs) {
		return ExpirableTxnRecord.newBuilder()
				.setTxnId(TxnId.fromGrpc(TransactionID.newBuilder()
						.setAccountID(payer)
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(validStartSecs)).build()))
				.setConsensusTime(RichInstant.fromJava(Instant.now()))
				.setReceipt(TxnReceipt.newBuilder().setStatus(SUCCESS.name()).build())
				.build();
	}
}
