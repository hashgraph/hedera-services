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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleScheduleTest;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.state.submerkle.EntityId.fromGrpcAccountId;
import static com.hedera.services.utils.EntityNum.fromScheduleId;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_ADMIN_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
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
import static org.mockito.Mockito.verify;

class HederaScheduleStoreTest {
	private static final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
	private static final String entityMemo = "Some memo here";
	private static final RichInstant schedulingTXValidStart = new RichInstant(123, 456);
	private static final long expectedExpiry = 1_234_567L;
	private static final RichInstant consensusTime = new RichInstant(expectedExpiry, 0);
	private static final Key adminJKey = asKeyUnchecked(SCHEDULE_ADMIN_KT.asJKeyUnchecked());
	private static final Set<HederaFunctionality> whitelist = Set.of(
			HederaFunctionality.CryptoTransfer, HederaFunctionality.CryptoDelete, HederaFunctionality.TokenBurn);

	private static final ScheduleID created = IdUtils.asSchedule("0.0.333333");
	private static final AccountID schedulingAccount = IdUtils.asAccount("0.0.333");
	private static final AccountID payerId = IdUtils.asAccount("0.0.456");
	private static final AccountID anotherPayerId = IdUtils.asAccount("0.0.457");

	private static final EntityId entityPayer = fromGrpcAccountId(payerId);
	private static final EntityId entitySchedulingAccount = fromGrpcAccountId(schedulingAccount);

	private static final TransactionBody parentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
			adminJKey,
			entityMemo,
			entityPayer.toGrpcAccountId(),
			entitySchedulingAccount.toGrpcAccountId(),
			schedulingTXValidStart.toGrpc());

	private EntityIdSource ids;
	private MerkleMap<EntityNum, MerkleSchedule> schedules;
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	private HederaLedger hederaLedger;
	private GlobalDynamicProperties globalDynamicProperties;

	private MerkleSchedule schedule;
	private MerkleSchedule anotherSchedule;

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
		globalDynamicProperties = mock(GlobalDynamicProperties.class);

		accountsLedger = (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>) mock(
				TransactionalLedger.class);
		given(accountsLedger.exists(payerId)).willReturn(true);
		given(accountsLedger.exists(schedulingAccount)).willReturn(true);
		given(accountsLedger.get(payerId, IS_DELETED)).willReturn(false);
		given(accountsLedger.get(schedulingAccount, IS_DELETED)).willReturn(false);

		schedules = (MerkleMap<EntityNum, MerkleSchedule>) mock(MerkleMap.class);
		given(schedules.get(fromScheduleId(created))).willReturn(schedule);
		given(schedules.containsKey(fromScheduleId(created))).willReturn(true);

		subject = new HederaScheduleStore(globalDynamicProperties, ids, () -> schedules);
		subject.setAccountsLedger(accountsLedger);
		subject.setHederaLedger(hederaLedger);
	}

	@Test
	void rebuildsAsExpected() {
		final var expected = MerkleSchedule.from(parentTxn.toByteArray(), 0L);
		expected.setKey(EntityNum.fromLong(created.getScheduleNum()));
		final var captor = forClass(Consumer.class);
		final var expectedKey = expected.toContentAddressableView();

		subject.rebuildViews();

		verify(schedules).forEachNode(captor.capture());
		final var visitor = captor.getValue();

		visitor.accept(expected);

		final var extant = subject.getExtantSchedules();
		assertEquals(1, extant.size());
		assertTrue(extant.containsKey(expectedKey));
		assertEquals(created, extant.get(expectedKey).toGrpcScheduleId());
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
	void provisionalApplicationWorks() {
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
	void createProvisionallyImmediatelyRejectsNonWhitelistedTxn() {
		given(globalDynamicProperties.schedulingWhitelist()).willReturn(EnumSet.of(HederaFunctionality.TokenMint));
		final var mockCreation = mock(MerkleSchedule.class);
		given(mockCreation.scheduledFunction()).willReturn(HederaFunctionality.TokenBurn);

		final var outcome = subject.createProvisionally(mockCreation, consensusTime);

		assertEquals(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST, outcome.status());
		assertNull(outcome.created());
	}

	@Test
	void createProvisionallyWorks() {
		given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);

		final var expected = MerkleSchedule.from(parentTxn.toByteArray(), 0L);

		final var outcome = subject.createProvisionally(expected, consensusTime);

		assertEquals(OK, outcome.status());
		assertEquals(created, outcome.created());
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
		given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);

		final var outcome = subject.createProvisionally(
				MerkleSchedule.from(parentTxn.toByteArray(), 0L), consensusTime);

		assertEquals(INVALID_SCHEDULE_PAYER_ID, outcome.status());
		assertNull(outcome.created());
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
		given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);

		final var differentParentTxn = MerkleScheduleTest.scheduleCreateTxnWith(
				adminJKey,
				entityMemo,
				entityPayer.toGrpcAccountId(),
				IdUtils.asAccount("22.33.44"),
				schedulingTXValidStart.toGrpc());

		rejectWith(INVALID_SCHEDULE_ACCOUNT_ID, differentParentTxn);
	}

	@Test
	void rejectsCreateProvisionallyDeletedPayer() {
		given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);
		given(hederaLedger.isDeleted(payerId)).willReturn(true);

		rejectWith(INVALID_SCHEDULE_PAYER_ID, parentTxn);
	}

	@Test
	void rejectsCreateProvisionallyDeletedScheduler() {
		given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);
		given(hederaLedger.isDeleted(schedulingAccount)).willReturn(true);
		given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);

		rejectWith(INVALID_SCHEDULE_ACCOUNT_ID, parentTxn);
	}

	@Test
	void rejectsCreateProvisionallyWithMissingSchedulingAccount() {
		given(accountsLedger.exists(schedulingAccount)).willReturn(false);
		given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);

		rejectWith(INVALID_SCHEDULE_ACCOUNT_ID, parentTxn);
	}

	@Test
	void recognizesCollidingSchedule() {
		final var candSchedule = MerkleSchedule.from(parentTxn.toByteArray(), expectedExpiry);
		final var cav = candSchedule.toContentAddressableView();
		subject.getExtantSchedules().put(cav, fromScheduleId(created));

		final var scheduleIdPair = subject.lookupSchedule(parentTxn.toByteArray());

		assertEquals(Pair.of(created, schedule), scheduleIdPair);
	}

	@Test
	void recognizesCollisionWithPending() {
		final var candSchedule = MerkleSchedule.from(parentTxn.toByteArray(), expectedExpiry);
		subject.pendingCreation = candSchedule;
		subject.pendingId = created;

		final var scheduleIdPair = subject.lookupSchedule(parentTxn.toByteArray());

		assertEquals(Pair.of(created, candSchedule), scheduleIdPair);
	}

	@Test
	void understandsMissing() {
		final var expected = MerkleSchedule.from(parentTxn.toByteArray(), 0L);

		final var scheduleIdPair = subject.lookupSchedule(parentTxn.toByteArray());

		assertNull(scheduleIdPair.getLeft());
		assertEquals(expected, scheduleIdPair.getRight());
	}

	@Test
	void deletesAsExpected() {
		final var now = schedulingTXValidStart.toJava();
		given(schedules.getForModify(fromScheduleId(created))).willReturn(schedule);

		final var outcome = subject.deleteAt(created, now);

		verify(schedule).markDeleted(now);
		assertEquals(OK, outcome);
	}

	@Test
	void rejectsDeletionMissingAdminKey() {
		given(schedule.adminKey()).willReturn(Optional.empty());

		final var outcome = subject.deleteAt(created, schedulingTXValidStart.toJava());

		verify(schedules, never()).remove(fromScheduleId(created));
		assertEquals(SCHEDULE_IS_IMMUTABLE, outcome);
	}

	@Test
	void rejectsDeletionAlreadyDeleted() {
		given(schedule.isDeleted()).willReturn(true);

		final var outcome = subject.deleteAt(created, schedulingTXValidStart.toJava());

		assertEquals(SCHEDULE_ALREADY_DELETED, outcome);
	}

	@Test
	void rejectsExecutionWhenDeleted() {
		given(schedule.isDeleted()).willReturn(true);

		final var outcome = subject.markAsExecuted(created, consensusNow);

		assertEquals(SCHEDULE_ALREADY_DELETED, outcome);
	}

	@Test
	void rejectsExecutionWhenExecuted() {
		given(schedule.isExecuted()).willReturn(true);

		final var outcome = subject.markAsExecuted(created, consensusNow);

		assertEquals(SCHEDULE_ALREADY_EXECUTED, outcome);
	}

	@Test
	void rejectsDeletionMissingSchedule() {
		given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

		final var outcome = subject.deleteAt(created, schedulingTXValidStart.toJava());

		verify(schedules, never()).remove(fromScheduleId(created));
		assertEquals(INVALID_SCHEDULE_ID, outcome);
	}

	@Test
	void rejectsExecutionMissingSchedule() {
		given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

		final var outcome = subject.markAsExecuted(created, consensusNow);

		assertEquals(INVALID_SCHEDULE_ID, outcome);
	}

	@Test
	void marksExecutedAsExpected() {
		given(globalDynamicProperties.triggerTxnWindBackNanos()).willReturn(11L);
		given(schedules.getForModify(fromScheduleId(created))).willReturn(schedule);

		subject.markAsExecuted(created, consensusNow);

		verify(schedule).markExecuted(consensusNow.plusNanos(11L));
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
		final var expected = MerkleSchedule.from(parentTxn.toByteArray(), 0L);

		subject.createProvisionally(expected, consensusTime);

		assertThrows(IllegalArgumentException.class,
				() -> subject.expire(EntityId.fromGrpcScheduleId(subject.pendingId)));
	}

	@Test
	void throwsUsoOnDelete() {
		assertThrows(UnsupportedOperationException.class, () -> subject.delete(created));
	}

	private void rejectWith(final ResponseCodeEnum expectedCode, final TransactionBody parentTxn) {
		final var outcome = subject.createProvisionally(
				MerkleSchedule.from(parentTxn.toByteArray(), 0L), consensusTime);

		assertEquals(expectedCode, outcome.status());
		assertNull(outcome.created());
		assertNull(subject.pendingCreation);
		assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
	}
}
