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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleScheduleTest;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.swirlds.fcmap.FCMap;
import com.swirlds.merkletree.MerklePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.function.Consumer;

import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.state.merkle.MerkleEntityId.fromScheduleId;
import static com.hedera.services.state.submerkle.EntityId.fromGrpcAccountId;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_ADMIN_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HederaScheduleStoreTest {
	private static final String entityMemo = "Some memo here";
	private static final RichInstant schedulingTXValidStart = new RichInstant(123, 456);
	private static final long expectedExpiry = 1_234_567L;
	private static final RichInstant consensusTime = new RichInstant(expectedExpiry, 0);
	private static final Key adminJKey = asKeyUnchecked(SCHEDULE_ADMIN_KT.asJKeyUnchecked());

	private static final ScheduleID created = IdUtils.asSchedule("1.2.333333");
	private static final AccountID schedulingAccount = IdUtils.asAccount("1.2.333");
	private static final AccountID payerId = IdUtils.asAccount("1.2.456");
	private static final AccountID anotherPayerId = IdUtils.asAccount("1.2.457");

	private static final EntityId entityPayer = fromGrpcAccountId(payerId);
	private static final EntityId entitySchedulingAccount = fromGrpcAccountId(schedulingAccount);

	private EntityIdSource ids;
	private FCMap<MerkleEntityId, MerkleSchedule> schedules;
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	private HederaLedger hederaLedger;
	private GlobalDynamicProperties globalDynamicProperties;

	private MerkleSchedule schedule;
	private MerkleSchedule anotherSchedule;
	private TransactionContext txnCtx;

	private HederaScheduleStore subject;

	@BeforeEach
	void setup() {
		schedule = mock(MerkleSchedule.class);
		anotherSchedule = mock(MerkleSchedule.class);

		given(schedule.hasAdminKey()).willReturn(true);
		given(schedule.adminKey()).willReturn(Optional.of(SCHEDULE_ADMIN_KT.asJKeyUnchecked()));
		given(schedule.payer()).willReturn(fromGrpcAccountId(payerId));
		given(schedule.memo()).willReturn(Optional.of(entityMemo));

		given(anotherSchedule.payer()).willReturn(fromGrpcAccountId(anotherPayerId));

		ids = mock(EntityIdSource.class);
		given(ids.newScheduleId(schedulingAccount)).willReturn(created);

		hederaLedger = mock(HederaLedger.class);
		txnCtx = mock(TransactionContext.class);
		globalDynamicProperties = mock(GlobalDynamicProperties.class);

		accountsLedger = (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>) mock(
				TransactionalLedger.class);
		given(accountsLedger.exists(payerId)).willReturn(true);
		given(accountsLedger.exists(schedulingAccount)).willReturn(true);
		given(accountsLedger.get(payerId, IS_DELETED)).willReturn(false);
		given(accountsLedger.get(schedulingAccount, IS_DELETED)).willReturn(false);

		schedules = (FCMap<MerkleEntityId, MerkleSchedule>) mock(FCMap.class);
		given(schedules.get(fromScheduleId(created))).willReturn(schedule);
		given(schedules.containsKey(fromScheduleId(created))).willReturn(true);

		subject = new HederaScheduleStore(globalDynamicProperties, ids, () -> schedules);
		subject.setAccountsLedger(accountsLedger);
		subject.setHederaLedger(hederaLedger);
		subject.setTxnCtx(txnCtx);
	}

	@Test
	void rebuildsAsExpected() {
		final var parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				entityPayer.toGrpcAccountId(),
				entitySchedulingAccount.toGrpcAccountId(),
				schedulingTXValidStart.toGrpc());
		final var expected = MerkleSchedule.from(parentTxn.toByteArray(), 0L);
		final var captor = forClass(Consumer.class);
		final var expectedKey = expected.toContentAddressableView();

		subject.rebuildViews();

		verify(schedules, times(2)).forEachNode(captor.capture());
		final var visitor = captor.getAllValues().get(1);

		visitor.accept(new MerklePair<>(fromScheduleId(created), expected));

		final var extant = subject.getExtantSchedules();
		assertEquals(1, extant.size());
		assertTrue(extant.containsKey(expectedKey));
		assertEquals(created, extant.get(expectedKey).toScheduleId());
	}

	@Test
	void commitAndRollbackThrowIseIfNoPendingCreation() {
		assertThrows(IllegalStateException.class, subject::commitCreation);
		assertThrows(IllegalStateException.class, subject::rollbackCreation);
	}

	@Test
	void commitPutsToMapAndClears() {
		subject.pendingId = created;
		subject.pendingCreation = schedule;
		given(schedule.toContentAddressableView()).willReturn(schedule);

		subject.commitCreation();

		verify(schedule).toContentAddressableView();
		verify(schedules).put(fromScheduleId(created), schedule);

		assertTrue(subject.getExtantSchedules().containsKey(schedule));
		assertSame(HederaScheduleStore.NO_PENDING_ID, subject.pendingId);
		assertNull(subject.pendingCreation);
	}

	@Test
	void rollbackReclaimsIdAndClears() {
		subject.pendingId = created;
		subject.pendingCreation = schedule;

		subject.rollbackCreation();

		verify(schedules, never()).put(fromScheduleId(created), schedule);
		verify(ids).reclaimLastId();

		assertSame(HederaScheduleStore.NO_PENDING_ID, subject.pendingId);
		assertNull(subject.pendingCreation);
	}

	@Test
	void understandsPendingCreation() {
		assertFalse(subject.isCreationPending());

		subject.pendingId = created;

		assertTrue(subject.isCreationPending());
	}

	@Test
	void getThrowsIseOnMissing() {
		given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

		assertThrows(IllegalArgumentException.class, () -> subject.get(created));
	}

	@Test
	void applicationRejectsMissing() {
		final var change = mock(Consumer.class);

		given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

		assertThrows(IllegalArgumentException.class, () -> subject.apply(created, change));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void provisionalApplicationWorks() {
		final var change = mock(Consumer.class);
		subject.pendingId = created;
		subject.pendingCreation = schedule;

		subject.apply(created, change);

		verify(change).accept(schedule);
		verify(schedules, never()).getForModify(fromScheduleId(created));
	}

	@Test
	void applicationWorks() {
		final var change = mock(Consumer.class);
		given(schedules.getForModify(fromScheduleId(created))).willReturn(schedule);
		final var inOrder = Mockito.inOrder(change, schedules);

		subject.apply(created, change);

		inOrder.verify(schedules).getForModify(fromScheduleId(created));
		inOrder.verify(change).accept(schedule);
	}

	@Test
	void applicationAlwaysReplacesModifiableSchedule() {
		final var change = mock(Consumer.class);
		final var key = fromScheduleId(created);
		given(schedules.getForModify(key)).willReturn(schedule);

		willThrow(IllegalStateException.class).given(change).accept(any());

		assertThrows(IllegalArgumentException.class, () -> subject.apply(created, change));
	}

	@Test
	void createProvisionallyWorks() {
		final var parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				entityPayer.toGrpcAccountId(),
				entitySchedulingAccount.toGrpcAccountId(),
				schedulingTXValidStart.toGrpc());
		final var expected = MerkleSchedule.from(parentTxn.toByteArray(), 0L);

		final var outcome = subject.createProvisionally(expected, consensusTime);

		assertEquals(OK, outcome.getStatus());
		assertEquals(created, outcome.getCreated().get());
		assertEquals(created, subject.pendingId);
		assertSame(expected, subject.pendingCreation);
		assertEquals(expectedExpiry, expected.expiry());
	}

	@Test
	void createProvisionallyRejectsInvalidPayer() {
		final var parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				IdUtils.asAccount("22.33.44"),
				entitySchedulingAccount.toGrpcAccountId(),
				schedulingTXValidStart.toGrpc());

		final var outcome = subject.createProvisionally(
				MerkleSchedule.from(parentTxn.toByteArray(), 0L), consensusTime);

		assertEquals(INVALID_SCHEDULE_PAYER_ID, outcome.getStatus());
		assertTrue(outcome.getCreated().isEmpty());
	}

	@Test
	void getCanReturnPending() {
		subject.pendingId = created;
		subject.pendingCreation = schedule;

		assertSame(schedule, subject.get(created));
	}

	@Test
	void existenceCheckUnderstandsPendingIdOnlyAppliesIfCreationPending() {
		assertFalse(subject.exists(HederaScheduleStore.NO_PENDING_ID));
	}

	@Test
	void createProvisionallyRejectsInvalidScheduler() {
		final var parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				entityPayer.toGrpcAccountId(),
				IdUtils.asAccount("22.33.44"),
				schedulingTXValidStart.toGrpc());

		final var outcome = subject.createProvisionally(
				MerkleSchedule.from(parentTxn.toByteArray(), 0L), consensusTime);

		assertEquals(INVALID_SCHEDULE_ACCOUNT_ID, outcome.getStatus());
		assertEquals(Optional.empty(), outcome.getCreated());
		assertNull(subject.pendingCreation);
		assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
	}

	@Test
	void rejectsCreateProvisionallyDeletedPayer() {
		given(hederaLedger.isDeleted(payerId)).willReturn(true);
		final var parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				entityPayer.toGrpcAccountId(),
				entitySchedulingAccount.toGrpcAccountId(),
				schedulingTXValidStart.toGrpc());

		final var outcome = subject.createProvisionally(
				MerkleSchedule.from(parentTxn.toByteArray(), 0L), consensusTime);

		assertEquals(INVALID_SCHEDULE_PAYER_ID, outcome.getStatus());
		assertEquals(Optional.empty(), outcome.getCreated());
		assertNull(subject.pendingCreation);
		assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
	}

	@Test
	void rejectsCreateProvisionallyDeletedScheduler() {
		given(hederaLedger.isDeleted(schedulingAccount)).willReturn(true);
		final var parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				entityPayer.toGrpcAccountId(),
				entitySchedulingAccount.toGrpcAccountId(),
				schedulingTXValidStart.toGrpc());

		final var outcome = subject.createProvisionally(
				MerkleSchedule.from(parentTxn.toByteArray(), 0L), consensusTime);

		assertEquals(INVALID_SCHEDULE_ACCOUNT_ID, outcome.getStatus());
		assertEquals(Optional.empty(), outcome.getCreated());
		assertNull(subject.pendingCreation);
		assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
	}

	@Test
	void rejectsCreateProvisionallyWithMissingSchedulingAccount() {
		given(accountsLedger.exists(schedulingAccount)).willReturn(false);
		final var parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				entityPayer.toGrpcAccountId(),
				entitySchedulingAccount.toGrpcAccountId(),
				schedulingTXValidStart.toGrpc());

		final var outcome = subject.createProvisionally(
				MerkleSchedule.from(parentTxn.toByteArray(), 0L), consensusTime);

		assertEquals(INVALID_SCHEDULE_ACCOUNT_ID, outcome.getStatus());
		assertEquals(Optional.empty(), outcome.getCreated());
		assertNull(subject.pendingCreation);
		assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
	}

	@Test
	void recognizesCollidingSchedule() {
		final var parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				entityPayer.toGrpcAccountId(),
				entitySchedulingAccount.toGrpcAccountId(),
				schedulingTXValidStart.toGrpc());
		final var candSchedule = MerkleSchedule.from(parentTxn.toByteArray(), expectedExpiry);
		final var cav = candSchedule.toContentAddressableView();
		subject.getExtantSchedules().put(cav, fromScheduleId(created));

		final var scheduleIdPair = subject.lookupSchedule(parentTxn.toByteArray());

		assertEquals(Pair.of(Optional.of(created), schedule), scheduleIdPair);
	}

	@Test
	void recognizesCollisionWithPending() {
		final var parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				entityPayer.toGrpcAccountId(),
				entitySchedulingAccount.toGrpcAccountId(),
				schedulingTXValidStart.toGrpc());
		final var candSchedule = MerkleSchedule.from(parentTxn.toByteArray(), expectedExpiry);
		subject.pendingCreation = candSchedule;
		subject.pendingId = created;

		final var scheduleIdPair = subject.lookupSchedule(parentTxn.toByteArray());

		assertEquals(Pair.of(Optional.of(created), candSchedule), scheduleIdPair);
	}

	@Test
	void understandsMissing() {
		final var parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				entityPayer.toGrpcAccountId(),
				entitySchedulingAccount.toGrpcAccountId(),
				schedulingTXValidStart.toGrpc());
		final var expected = MerkleSchedule.from(parentTxn.toByteArray(), 0L);

		final var scheduleIdPair = subject.lookupSchedule(parentTxn.toByteArray());

		assertTrue(scheduleIdPair.getLeft().isEmpty());
		assertEquals(expected, scheduleIdPair.getRight());
	}

	@Test
	void deletesAsExpected() {
		final var now = schedulingTXValidStart.toJava();
		given(schedules.getForModify(fromScheduleId(created))).willReturn(schedule);
		given(txnCtx.consensusTime()).willReturn(now);

		final var outcome = subject.delete(created);

		verify(schedule).markDeleted(now);
		assertEquals(OK, outcome);
	}

	@Test
	void rejectsDeletionMissingAdminKey() {
		given(schedule.adminKey()).willReturn(Optional.empty());

		final var outcome = subject.delete(created);

		verify(schedules, never()).remove(fromScheduleId(created));
		assertEquals(SCHEDULE_IS_IMMUTABLE, outcome);
	}

	@Test
	void rejectsDeletionAlreadyDeleted() {
		given(schedule.isDeleted()).willReturn(true);

		final var outcome = subject.delete(created);

		assertEquals(SCHEDULE_ALREADY_DELETED, outcome);
	}

	@Test
	void rejectsExecutionWhenDeleted() {
		given(schedule.isDeleted()).willReturn(true);

		final var outcome = subject.markAsExecuted(created);

		assertEquals(SCHEDULE_ALREADY_DELETED, outcome);
	}

	@Test
	void rejectsExecutionWhenExecuted() {
		given(schedule.isExecuted()).willReturn(true);

		final var outcome = subject.markAsExecuted(created);

		assertEquals(SCHEDULE_ALREADY_EXECUTED, outcome);
	}

	@Test
	void rejectsDeletionMissingSchedule() {
		given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

		final var outcome = subject.delete(created);

		verify(schedules, never()).remove(fromScheduleId(created));
		assertEquals(INVALID_SCHEDULE_ID, outcome);
	}

	@Test
	void rejectsExecutionMissingSchedule() {
		given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

		final var outcome = subject.markAsExecuted(created);

		assertEquals(INVALID_SCHEDULE_ID, outcome);
	}

	@Test
	void marksExecutedAsExpected() {
		final var now = schedulingTXValidStart.toJava();
		given(txnCtx.consensusTime()).willReturn(now);
		given(schedules.getForModify(fromScheduleId(created))).willReturn(schedule);

		subject.markAsExecuted(created);

		verify(schedule).markExecuted(now.plusNanos(1L));
		verify(schedules, never()).remove(fromScheduleId(created));
	}

	@Test
	void expiresAsExpected() {
		subject.getExtantSchedules().put(schedule, fromScheduleId(created));

		subject.expire(EntityId.fromGrpcScheduleId(created));

		verify(schedules).remove(fromScheduleId(created));
		assertFalse(subject.getExtantSchedules().containsKey(schedule));
	}

	@Test
	void throwsOnExpiringMissingSchedule() {
		given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

		assertThrows(IllegalArgumentException.class, () -> subject.expire(EntityId.fromGrpcScheduleId(created)));
	}

	@Test
	void throwsOnExpiringPending() {
		final var parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				entityPayer.toGrpcAccountId(),
				entitySchedulingAccount.toGrpcAccountId(),
				schedulingTXValidStart.toGrpc());
		final var expected = MerkleSchedule.from(parentTxn.toByteArray(), 0L);

		subject.createProvisionally(expected, consensusTime);

		assertThrows(IllegalArgumentException.class,
				() -> subject.expire(EntityId.fromGrpcScheduleId(subject.pendingId)));
	}
}
