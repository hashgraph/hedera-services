package com.hedera.services.txns.schedule;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduleReadyForExecutionTest {
	private ScheduleID id = IdUtils.asSchedule("0.0.1234");

	@Mock
	private ScheduleStore store;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private MerkleSchedule schedule;

	private ScheduleReadyForExecution subject;

	@BeforeEach
	void setUp() {
		subject = new ScheduleReadyForExecution(store, txnCtx);
	}

	@Test
	void triggersIfCanMarkAsExecuted() throws InvalidProtocolBufferException {
		given(store.markAsExecuted(id)).willReturn(OK);
		given(store.get(id)).willReturn(schedule);
		given(schedule.asSignedTxn()).willReturn(Transaction.getDefaultInstance());
		given(schedule.effectivePayer()).willReturn(new EntityId(0, 0, 4321));

		// when:
		var result = subject.processExecution(id);

		// then:
		verify(txnCtx).trigger(any());
		// and:
		Assertions.assertEquals(OK, result);
	}

	@Test
	void doesntTriggerUnlessAbleToMarkScheduleExecuted() throws InvalidProtocolBufferException {
		given(store.markAsExecuted(id)).willReturn(SCHEDULE_ALREADY_EXECUTED);

		// when:
		var result = subject.processExecution(id);

		// then:
		verify(txnCtx, never()).trigger(any());
		// and:
		Assertions.assertEquals(SCHEDULE_ALREADY_EXECUTED, result);
	}
}