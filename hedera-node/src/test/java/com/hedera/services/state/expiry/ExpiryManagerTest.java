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
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
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
	private final long now = 1_234_567L;
	private final long start = now - 180L;
	private final long firstThen = now - 1;
	private final long secondThen = now + 1;
	private final AccountID aGrpcId = IdUtils.asAccount("0.0.2");
	private final AccountID bGrpcId = IdUtils.asAccount("0.0.4");
	private final PermHashInteger aKey = PermHashInteger.fromAccountId(aGrpcId);
	private final PermHashInteger bKey = PermHashInteger.fromAccountId(bGrpcId);
	private final MerkleAccount anAccount = new MerkleAccount();
	private final MerkleSchedule aSchedule = new MerkleSchedule();
	private final MerkleSchedule bSchedule = new MerkleSchedule();

	private MerkleMap<PermHashInteger, MerkleAccount> liveAccounts = new MerkleMap<>();
	private MerkleMap<PermHashInteger, MerkleSchedule> liveSchedules = new MerkleMap<>();
	private Map<TransactionID, TxnIdRecentHistory> liveTxnHistories = new HashMap<>();

	private final HederaNumbers nums = new MockHederaNumbers();

	@Mock
	private ScheduleStore mockScheduleStore;
	@Mock
	private Map<TransactionID, TxnIdRecentHistory> mockTxnHistories;
	@Mock
	private MerkleMap<PermHashInteger, MerkleAccount> mockAccounts;
	@Mock
	private MerkleMap<PermHashInteger, MerkleSchedule> mockSchedules;

	private ExpiryManager subject;

	@Test
	void rebuildsExpectedSchedulesFromState() {
		// setup:
		subject = new ExpiryManager(
				mockScheduleStore, nums, mockTxnHistories, () -> mockAccounts, () -> liveSchedules);
		aSchedule.setExpiry(firstThen);
		bSchedule.setExpiry(secondThen);
		liveSchedules.put(aKey, aSchedule);
		liveSchedules.put(bKey, bSchedule);

		// when:
		subject.reviewExistingShortLivedEntities();
		// and:
		final var resultingExpiries = subject.getShortLivedEntityExpiries();
		final var firstExpiry = resultingExpiries.expireNextAt(now);

		// then:
		assertEquals(aKey.longValue(), firstExpiry.getLeft());
		assertEquals(1, resultingExpiries.getAllExpiries().size());
	}

	@Test
	void expiresSchedulesAsExpected() {
		subject = new ExpiryManager(
				mockScheduleStore, nums, mockTxnHistories, () -> mockAccounts, () -> mockSchedules);

		// given:
		subject.trackExpirationEvent(Pair.of((long) aKey.longValue(),
				entityId -> mockScheduleStore.expire(entityId)), firstThen);
		subject.trackExpirationEvent(Pair.of((long) bKey.longValue(),
				entityId -> mockScheduleStore.expire(entityId)), secondThen);

		// when:
		subject.purge(now);

		// then:
		verify(mockScheduleStore).expire(new EntityId(0, 0, aKey.longValue()));
		assertEquals(1, subject.getShortLivedEntityExpiries().getAllExpiries().size());
	}

	@Test
	void rebuildsExpectedRecordsFromState() {
		// setup:
		subject = new ExpiryManager(
				mockScheduleStore, nums, liveTxnHistories, () -> liveAccounts, () -> mockSchedules);
		final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
		final var leftoverTxnId = recordWith(bGrpcId, now).getTxnId().toGrpc();
		liveTxnHistories.put(leftoverTxnId, new TxnIdRecentHistory());

		// given:
		anAccount.records().offer(expiring(recordWith(aGrpcId, start), firstThen));
		anAccount.records().offer(expiring(recordWith(aGrpcId, start), secondThen));
		liveAccounts.put(aKey, anAccount);

		// when:
		subject.reviewExistingPayerRecords();

		// then:
		assertFalse(liveTxnHistories.containsKey(leftoverTxnId));
		assertEquals(firstThen, liveTxnHistories.get(newTxnId).priorityRecord().getExpiry());
		assertEquals(secondThen, liveTxnHistories.get(newTxnId).duplicateRecords().get(0).getExpiry());
	}

	@Test
	void expiresRecordsAsExpected() {
		// setup:
		subject = new ExpiryManager(
				mockScheduleStore, nums, liveTxnHistories, () -> liveAccounts, () -> mockSchedules);
		final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
		liveAccounts.put(aKey, anAccount);

		// given:
		final var firstRecord = expiring(recordWith(aGrpcId, start), firstThen);
		addLiveRecord(aKey, firstRecord);
		liveTxnHistories.computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory()).observe(firstRecord, OK);
		subject.trackRecordInState(aGrpcId, firstThen);
		// and:
		final var secondRecord = expiring(recordWith(aGrpcId, start), secondThen);
		addLiveRecord(aKey, secondRecord);
		liveTxnHistories.computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory()).observe(secondRecord, OK);
		subject.trackRecordInState(aGrpcId, secondThen);

		// when:
		subject.purge(now);

		// then:
		assertEquals(1, liveAccounts.get(aKey).records().size());
		assertEquals(secondThen, liveTxnHistories.get(newTxnId).priorityRecord().getExpiry());
	}

	@Test
	void expiresLoneRecordAsExpected() {
		// setup:
		subject = new ExpiryManager(
				mockScheduleStore, nums, liveTxnHistories, () -> liveAccounts, () -> mockSchedules);
		final var newTxnId = recordWith(aGrpcId, start).getTxnId().toGrpc();
		liveAccounts.put(aKey, anAccount);

		// given:
		final var firstRecord = expiring(recordWith(aGrpcId, start), firstThen);
		addLiveRecord(aKey, firstRecord);
		liveTxnHistories.computeIfAbsent(newTxnId, ignore -> new TxnIdRecentHistory()).observe(firstRecord, OK);
		subject.trackRecordInState(aGrpcId, firstThen);

		// when:
		subject.purge(now);

		// then:
		assertEquals(0, liveAccounts.get(aKey).records().size());
		assertFalse(liveTxnHistories.containsKey(newTxnId));
	}

	private void addLiveRecord(PermHashInteger key, ExpirableTxnRecord record) {
		final var mutableAccount = liveAccounts.getForModify(key);
		mutableAccount.records().offer(record);
		liveAccounts.replace(aKey, mutableAccount);
	}

	private ExpirableTxnRecord expiring(ExpirableTxnRecord record, long at) {
		final var ans = record;
		ans.setExpiry(at);
		ans.setSubmittingMember(0L);
		return ans;
	}

	private ExpirableTxnRecord recordWith(AccountID payer, long validStartSecs) {
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
