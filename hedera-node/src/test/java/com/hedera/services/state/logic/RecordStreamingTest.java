package com.hedera.services.state.logic;

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
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stream.NonBlockingHandoff;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.crypto.RunningHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
class RecordStreamingTest {
	private static final Instant topLevelConsTime = Instant.ofEpochSecond(1_234_567L, 890);

	@Mock
	private TransactionContext txnCtx;
	@Mock
	private NonBlockingHandoff nonBlockingHandoff;
	@Mock
	private Consumer<RunningHash> runningHashUpdate;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private TxnAccessor accessor;
	@Mock
	private RecordStreamObject firstFollowingChildRso;
	@Mock
	private RecordStreamObject secondFollowingChildRso;
	@Mock
	private RecordStreamObject firstPrecedingChildRso;

	private RecordStreaming subject;

	@BeforeEach
	void setUp() {
		subject = new RecordStreaming(txnCtx, nonBlockingHandoff, runningHashUpdate, recordsHistorian);
	}

	@Test
	void streamsChildRecordsAtExpectedTimes() {
		given(recordsHistorian.hasPrecedingChildRecords()).willReturn(true);
		given(recordsHistorian.getPrecedingChildRecords()).willReturn(List.of(
				firstPrecedingChildRso));
		given(recordsHistorian.hasFollowingChildRecords()).willReturn(true);
		given(recordsHistorian.getFollowingChildRecords()).willReturn(List.of(
				firstFollowingChildRso, secondFollowingChildRso));
		given(nonBlockingHandoff.offer(firstPrecedingChildRso)).willReturn(true);
		given(nonBlockingHandoff.offer(firstFollowingChildRso)).willReturn(true);
		given(nonBlockingHandoff.offer(secondFollowingChildRso)).willReturn(true);

		subject.run();

		verify(nonBlockingHandoff).offer(firstPrecedingChildRso);
		verify(nonBlockingHandoff).offer(firstFollowingChildRso);
		verify(nonBlockingHandoff).offer(secondFollowingChildRso);
	}

	@Test
	void doesNothingIfNoRecord() {
		// when:
		subject.run();

		// then:
		verifyNoInteractions(txnCtx, nonBlockingHandoff, runningHashUpdate);
	}

	@Test
	void streamsWhenAvail() {
		final var txn = Transaction.getDefaultInstance();
		final var lastRecord = ExpirableTxnRecord.newBuilder().build();
		final var expectedRso = new RecordStreamObject(lastRecord, txn, topLevelConsTime);
		given(accessor.getSignedTxnWrapper()).willReturn(txn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(topLevelConsTime);
		given(recordsHistorian.lastCreatedTopLevelRecord()).willReturn(lastRecord);
		given(nonBlockingHandoff.offer(expectedRso))
				.willReturn(false)
				.willReturn(true);

		subject.run();

		verify(nonBlockingHandoff, times(2)).offer(expectedRso);
	}
}
