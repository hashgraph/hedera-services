package com.hedera.services.txns.schedule;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.keys.CharacteristicsFactory;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.logic.SigsAndPayerKeyScreen;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.throttling.TimedFunctionalityThrottling;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.TreeMap;

import static com.hedera.services.utils.EntityNum.fromScheduleId;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_THROTTLE_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ScheduleProcessingTest {

	private static Instant consensusTime = Instant.EPOCH;
	private static final ScheduleID scheduleId1 = IdUtils.asSchedule("0.0.133333");
	private static final ScheduleID scheduleId2 = IdUtils.asSchedule("0.0.233333");
	private static final ScheduleID scheduleId3 = IdUtils.asSchedule("0.0.333333");
	private static final ScheduleID scheduleId4 = IdUtils.asSchedule("0.0.433333");
	private static final ScheduleID scheduleId5 = IdUtils.asSchedule("0.0.533333");

	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private ScheduleStore store;
	@Mock
	private ScheduleExecutor scheduleExecutor;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private SigsAndPayerKeyScreen sigsAndPayerKeyScreen;
	@Mock
	private CharacteristicsFactory characteristics;
	@Mock
	private TimedFunctionalityThrottling scheduleThrottling;
	@Mock
	private ScheduleVirtualValue schedule1;
	@Mock
	private ScheduleVirtualValue schedule2;
	@Mock
	private ScheduleVirtualValue schedule3;
	@Mock
	private ScheduleVirtualValue schedule4;
	@Mock
	private ScheduleSecondVirtualValue bySecond;
	@Mock
	private TxnAccessor accessor;

	private ScheduleProcessing subject;

	@BeforeEach
	void setUp() {
		subject = new ScheduleProcessing(sigImpactHistorian, store, scheduleExecutor, dynamicProperties,
				sigsAndPayerKeyScreen, characteristics, scheduleThrottling);
	}

	@Test
	void expireWorksAsExpected() {
		var inOrder = Mockito.inOrder(store, sigImpactHistorian);
		given(store.nextSchedulesToExpire(consensusTime))
				.willReturn(ImmutableList.of(scheduleId1, scheduleId2), ImmutableList.of(scheduleId3), ImmutableList.of());

		// when:
		subject.expire(consensusTime);

		// then:
		inOrder.verify(store).advanceCurrentMinSecond(consensusTime);
		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(store).expire(scheduleId1);
		inOrder.verify(sigImpactHistorian).markEntityChanged(fromScheduleId(scheduleId1).longValue());
		inOrder.verify(store).expire(scheduleId2);
		inOrder.verify(sigImpactHistorian).markEntityChanged(fromScheduleId(scheduleId2).longValue());
		inOrder.verify(store).advanceCurrentMinSecond(consensusTime);
		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(store).expire(scheduleId3);
		inOrder.verify(sigImpactHistorian).markEntityChanged(fromScheduleId(scheduleId3).longValue());
		inOrder.verify(store).advanceCurrentMinSecond(consensusTime);
		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(store, never()).expire(any());
		inOrder.verify(sigImpactHistorian, never()).markEntityChanged(anyLong());

		inOrder.verifyNoMoreInteractions();
	}

	@Test
	void triggerNextTransactionExpiringAsNeededWorksAsExpected() throws InvalidProtocolBufferException {
		var inOrder = Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, sigsAndPayerKeyScreen,
				characteristics, scheduleExecutor);
		given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
		given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

		given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1);

		given(store.get(scheduleId1)).willReturn(schedule1);
		given(schedule1.parentAsSignedTxn()).willReturn(getSignedTxn());

		subject.isReady = (k, b) -> {
			assertEquals(k, schedule1);
			assertNotNull(b);
			return true;
		};

		given(scheduleExecutor.getTriggeredTxnAccessor(scheduleId1, store, false)).willReturn(Pair.of(OK, accessor));

		// when:
		var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null);

		// then:

		assertEquals(result, accessor);

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
		inOrder.verify(store).get(scheduleId1);
		inOrder.verify(sigsAndPayerKeyScreen).applyTo(argThat(a -> a.getTxn().getMemo().equals("scheduled")),
				argThat(Optional::isEmpty));
		inOrder.verify(scheduleExecutor).getTriggeredTxnAccessor(scheduleId1, store, false);

		inOrder.verifyNoMoreInteractions();
	}

	@Test
	void triggerNextTransactionExpiringAsNeededErrorsOnSameIdTwiceFromExternal() {
		var inOrder = Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, sigsAndPayerKeyScreen,
				characteristics, scheduleExecutor);
		given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
		given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

		given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1);
		given(accessor.getScheduleRef()).willReturn(scheduleId1);

		// when:
		assertThrows(IllegalStateException.class, () ->
				subject.triggerNextTransactionExpiringAsNeeded(consensusTime, accessor));

		// then:

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);

		inOrder.verifyNoMoreInteractions();
	}
	@Test
	void triggerNextTransactionExpiringAsNeededSkipsOnLongTermDisabled() {
		var inOrder = Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, sigsAndPayerKeyScreen,
				characteristics, scheduleExecutor);
		given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
		given(dynamicProperties.schedulingLongTermEnabled()).willReturn(false);

		// when:
		var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null);

		// then:

		assertNull(result);

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();

		inOrder.verifyNoMoreInteractions();
	}


	@Test
	void triggerNextTransactionExpiringAsNeededHandlesNotReady() throws InvalidProtocolBufferException {
		var inOrder = Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, sigsAndPayerKeyScreen,
				characteristics, scheduleExecutor);
		given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
		given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

		given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1, scheduleId2);

		given(store.get(scheduleId1)).willReturn(schedule1);
		given(store.get(scheduleId2)).willReturn(schedule2);
		given(schedule1.parentAsSignedTxn()).willReturn(getSignedTxn());
		given(schedule2.parentAsSignedTxn()).willReturn(getSignedTxn());

		subject.isReady = (k, b) -> {
			assertNotNull(b);
			if (k == schedule1) {
				return false;
			}
			assertEquals(k, schedule2);
			return true;
		};

		given(scheduleExecutor.getTriggeredTxnAccessor(scheduleId2, store, false)).willReturn(Pair.of(OK, accessor));

		// when:
		var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null);

		// then:

		assertEquals(result, accessor);

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
		inOrder.verify(store).get(scheduleId1);
		inOrder.verify(sigsAndPayerKeyScreen).applyTo(argThat(a -> a.getTxn().getMemo().equals("scheduled")),
				argThat(Optional::isEmpty));
		inOrder.verify(store).expire(scheduleId1);
		inOrder.verify(sigImpactHistorian).markEntityChanged(fromScheduleId(scheduleId1).longValue());

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
		inOrder.verify(store).get(scheduleId2);
		inOrder.verify(sigsAndPayerKeyScreen).applyTo(argThat(a -> a.getTxn().getMemo().equals("scheduled")),
				argThat(Optional::isEmpty));
		inOrder.verify(scheduleExecutor).getTriggeredTxnAccessor(scheduleId2, store, false);

		inOrder.verifyNoMoreInteractions();
	}

	@Test
	void triggerNextTransactionExpiringAsNeededHandlesTriggerNotOk() throws InvalidProtocolBufferException {
		var inOrder = Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, sigsAndPayerKeyScreen,
				characteristics, scheduleExecutor);
		given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
		given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

		given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1, scheduleId2);

		given(store.get(scheduleId1)).willReturn(schedule1);
		given(store.get(scheduleId2)).willReturn(schedule2);
		given(schedule1.parentAsSignedTxn()).willReturn(getSignedTxn());
		given(schedule2.parentAsSignedTxn()).willReturn(getSignedTxn());

		subject.isReady = (k, b) -> {
			assertNotNull(b);
			if (k == schedule1) {
				return true;
			}
			assertEquals(k, schedule2);
			return true;
		};

		given(scheduleExecutor.getTriggeredTxnAccessor(scheduleId1, store, false)).willReturn(Pair.of(INVALID_SCHEDULE_ID, accessor));
		given(scheduleExecutor.getTriggeredTxnAccessor(scheduleId2, store, false)).willReturn(Pair.of(OK, accessor));

		// when:
		var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null);

		// then:

		assertEquals(result, accessor);

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
		inOrder.verify(store).get(scheduleId1);
		inOrder.verify(sigsAndPayerKeyScreen).applyTo(argThat(a -> a.getTxn().getMemo().equals("scheduled")),
				argThat(Optional::isEmpty));
		inOrder.verify(scheduleExecutor).getTriggeredTxnAccessor(scheduleId1, store, false);
		inOrder.verify(store).expire(scheduleId1);
		inOrder.verify(sigImpactHistorian).markEntityChanged(fromScheduleId(scheduleId1).longValue());

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
		inOrder.verify(store).get(scheduleId2);
		inOrder.verify(sigsAndPayerKeyScreen).applyTo(argThat(a -> a.getTxn().getMemo().equals("scheduled")),
				argThat(Optional::isEmpty));
		inOrder.verify(scheduleExecutor).getTriggeredTxnAccessor(scheduleId2, store, false);

		inOrder.verifyNoMoreInteractions();
	}

	@Test
	void triggerNextTransactionExpiringAsNeededHandlesErrorProcessing() throws InvalidProtocolBufferException {
		var inOrder = Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, sigsAndPayerKeyScreen,
				characteristics, scheduleExecutor);
		given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
		given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

		given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1, scheduleId2);

		given(store.get(scheduleId1)).willReturn(schedule1);
		given(store.get(scheduleId2)).willReturn(schedule2);
		given(schedule1.parentAsSignedTxn()).willReturn(getSignedTxn());
		given(schedule2.parentAsSignedTxn()).willReturn(getSignedTxn());

		subject.isReady = (k, b) -> {
			assertNotNull(b);
			if (k == schedule1) {
				throw new IllegalStateException();
			}
			assertEquals(k, schedule2);
			return true;
		};

		given(scheduleExecutor.getTriggeredTxnAccessor(scheduleId2, store, false)).willReturn(Pair.of(OK, accessor));

		// when:
		var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null);

		// then:

		assertEquals(result, accessor);

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
		inOrder.verify(store).get(scheduleId1);
		inOrder.verify(sigsAndPayerKeyScreen).applyTo(argThat(a -> a.getTxn().getMemo().equals("scheduled")),
				argThat(Optional::isEmpty));
		inOrder.verify(store).expire(scheduleId1);
		inOrder.verify(sigImpactHistorian).markEntityChanged(fromScheduleId(scheduleId1).longValue());

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
		inOrder.verify(store).get(scheduleId2);
		inOrder.verify(sigsAndPayerKeyScreen).applyTo(argThat(a -> a.getTxn().getMemo().equals("scheduled")),
				argThat(Optional::isEmpty));
		inOrder.verify(scheduleExecutor).getTriggeredTxnAccessor(scheduleId2, store, false);

		inOrder.verifyNoMoreInteractions();
	}

	@Test
	void triggerNextTransactionExpiringAsNeededErrorsOnSameIdTwiceFromInternal() {
		var inOrder = Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, sigsAndPayerKeyScreen,
				characteristics, scheduleExecutor);
		given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
		given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

		given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1, scheduleId1);

		given(store.get(scheduleId1)).willReturn(schedule1);
		given(schedule1.parentAsSignedTxn()).willReturn(getSignedTxn());

		subject.isReady = (k, b) -> {
			assertNotNull(b);
			assertEquals(k, schedule1);
			throw new IllegalStateException();
		};

		// when:
		assertThrows(IllegalStateException.class, () ->
				subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null));

		// then:

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
		inOrder.verify(store).get(scheduleId1);
		inOrder.verify(sigsAndPayerKeyScreen).applyTo(argThat(a -> a.getTxn().getMemo().equals("scheduled")),
				argThat(Optional::isEmpty));
		inOrder.verify(store).expire(scheduleId1);
		inOrder.verify(sigImpactHistorian).markEntityChanged(fromScheduleId(scheduleId1).longValue());

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);

		inOrder.verifyNoMoreInteractions();
	}

	@Test
	void triggerNextTransactionExpiringAsNeededSkipsOnNextNull() {
		var inOrder = Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, sigsAndPayerKeyScreen,
				characteristics, scheduleExecutor);
		given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
		given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

		given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1, null);

		given(store.get(scheduleId1)).willReturn(schedule1);
		given(schedule1.parentAsSignedTxn()).willReturn(getSignedTxn());

		subject.isReady = (k, b) -> {
			assertNotNull(b);
			assertEquals(k, schedule1);
			throw new IllegalStateException();
		};

		// when:
		var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null);

		// then:

		assertNull(result);

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
		inOrder.verify(store).get(scheduleId1);
		inOrder.verify(sigsAndPayerKeyScreen).applyTo(argThat(a -> a.getTxn().getMemo().equals("scheduled")),
				argThat(Optional::isEmpty));
		inOrder.verify(store).expire(scheduleId1);
		inOrder.verify(sigImpactHistorian).markEntityChanged(fromScheduleId(scheduleId1).longValue());

		inOrder.verify(store).nextSchedulesToExpire(consensusTime);
		inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
		inOrder.verify(store).nextScheduleToEvaluate(consensusTime);

		inOrder.verifyNoMoreInteractions();
	}

	@Test
	void checkFutureThrottlesForCreateWorksAsExpected() {
		var inOrder = Mockito.inOrder(scheduleThrottling);

		given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

		given(schedule1.calculatedExpirationTime()).willReturn(RichInstant.fromJava(consensusTime));

		given(store.getBySecond(consensusTime.getEpochSecond())).willReturn(bySecond);

		given(bySecond.getIds()).willReturn(new TreeMap<>(
				ImmutableMap.of(
						new RichInstant(consensusTime.getEpochSecond(), 0),
							LongLists.immutable.of(fromScheduleId(scheduleId1).longValue()),
						new RichInstant(consensusTime.getEpochSecond(), 2),
							LongLists.immutable.of(
									fromScheduleId(scheduleId2).longValue(),
									fromScheduleId(scheduleId3).longValue(),
									fromScheduleId(scheduleId5).longValue())
				)));

		given(schedule1.calculatedExpirationTime()).willReturn(new RichInstant(consensusTime.getEpochSecond(), 0));
		given(schedule2.calculatedExpirationTime()).willReturn(new RichInstant(consensusTime.getEpochSecond(), 2));
		given(schedule3.calculatedExpirationTime()).willReturn(new RichInstant(consensusTime.getEpochSecond() - 1, 0));
		given(schedule4.calculatedExpirationTime()).willReturn(new RichInstant(consensusTime.getEpochSecond(), 1));

		given(store.getNoError(scheduleId1)).willReturn(schedule1);
		given(store.getNoError(scheduleId2)).willReturn(schedule2);
		given(store.getNoError(scheduleId3)).willReturn(schedule3);

		given(schedule1.asSignedTxn()).willReturn(getSignedTxn(scheduleId1));
		given(schedule2.asSignedTxn()).willReturn(getSignedTxn(scheduleId2));
		given(schedule4.asSignedTxn()).willReturn(getSignedTxn(scheduleId4));

		given(scheduleThrottling.shouldThrottleTxn(any(TxnAccessor.class),
				eq(new RichInstant(consensusTime.getEpochSecond(), 0).toJava())))
				.willReturn(false);
		given(scheduleThrottling.shouldThrottleTxn(any(TxnAccessor.class),
				eq(new RichInstant(consensusTime.getEpochSecond(), 1).toJava())))
				.willReturn(false);
		given(scheduleThrottling.shouldThrottleTxn(any(TxnAccessor.class),
				eq(new RichInstant(consensusTime.getEpochSecond(), 2).toJava())))
				.willReturn(true);

		var result = subject.checkFutureThrottlesForCreate(schedule4);

		assertEquals(result, SCHEDULE_FUTURE_THROTTLE_EXCEEDED);

		inOrder.verify(scheduleThrottling).shouldThrottleTxn(any(TxnAccessor.class),
				eq(new RichInstant(consensusTime.getEpochSecond(), 0).toJava()));
		inOrder.verify(scheduleThrottling).shouldThrottleTxn(any(TxnAccessor.class),
				eq(new RichInstant(consensusTime.getEpochSecond(), 1).toJava()));
		inOrder.verify(scheduleThrottling).shouldThrottleTxn(any(TxnAccessor.class),
				eq(new RichInstant(consensusTime.getEpochSecond(), 2).toJava()));



		given(dynamicProperties.schedulingLongTermEnabled()).willReturn(false);

		result = subject.checkFutureThrottlesForCreate(schedule4);

		assertEquals(result, OK);


		given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);
		given(scheduleThrottling.wasLastTxnGasThrottled()).willReturn(true);

		result = subject.checkFutureThrottlesForCreate(schedule4);

		assertEquals(result, SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED);



		given(scheduleThrottling.shouldThrottleTxn(any(TxnAccessor.class),
				eq(new RichInstant(consensusTime.getEpochSecond(), 2).toJava())))
				.willReturn(false);

		result = subject.checkFutureThrottlesForCreate(schedule4);

		assertEquals(result, OK);

	}

	private Transaction getSignedTxn() {
		return getSignedTxn(null);
	}
	private Transaction getSignedTxn(ScheduleID scheduleID) {
		final var txnId = TransactionID.newBuilder().setAccountID(asAccount("0.0.1001")).build();
		return Transaction.newBuilder()
				.setBodyBytes(TransactionBody.newBuilder()
						.setTransactionID(txnId)
						.setMemo(scheduleID != null ? scheduleID.toString() : "scheduled")
						.build().toByteString())
				.build();
	}
}