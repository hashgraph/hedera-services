package com.hedera.services.legacy.services.state;

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

import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.state.logic.PayerSigValidity;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stream.NonBlockingHandoff;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.function.BiPredicate;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;


@ExtendWith({ LogCaptureExtension.class, MockitoExtension.class })
class TargetedAwareProcessLogicTest {
	private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L);

	@Mock
	private TxnAccessor txnAccessor;
	@Mock
	private ServicesContext ctx;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private RecordStreamManager recordStreamManager;
	@Mock
	private NonBlockingHandoff nonBlockingHandoff;
	@Mock
	private Rationalization rationalization;
	@Mock
	private BiPredicate<JKey, TransactionSignature> validityTest;
	@Mock
	private PayerSigValidity payerSigValidity;

	@LoggingTarget
	private LogCaptor logCaptor;

	@LoggingSubject
	private AwareProcessLogic subject;

	@BeforeEach
	void setUp() {
		subject = new AwareProcessLogic(ctx, rationalization, validityTest);
	}

	@Test
	void usesPayerSigValidityWithValidityTest() {
		// setup:
		subject.setPayerSigValidity(payerSigValidity);

		given(payerSigValidity.test(txnAccessor, validityTest)).willReturn(true);

		// expect:
		Assertions.assertTrue(subject.hasActivePayerSig(txnAccessor));
	}

	@Test
	void defaultsFalseIfValidityTestThrows() {
		// setup:
		subject.setPayerSigValidity(payerSigValidity);

		given(payerSigValidity.test(txnAccessor, validityTest)).willThrow(RuntimeException.class);

		// expect:
		Assertions.assertFalse(subject.hasActivePayerSig(txnAccessor));
		// and:
		Assertions.assertTrue(logCaptor.warnLogs().get(0)
				.startsWith("Unhandled exception when testing payer sig activation"));
	}

	@Test
	void streamsRecordIfPresent() {
		// setup:
		final Transaction txn = Transaction.getDefaultInstance();
		final ExpirableTxnRecord lastRecord = ExpirableTxnRecord.newBuilder().build();
		final RecordStreamObject expectedRso = new RecordStreamObject(lastRecord, txn, consensusNow);

		given(txnAccessor.getSignedTxnWrapper()).willReturn(txn);
		given(txnCtx.accessor()).willReturn(txnAccessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(recordsHistorian.lastCreatedRecord()).willReturn(Optional.of(lastRecord));
		given(ctx.recordsHistorian()).willReturn(recordsHistorian);
		given(ctx.txnCtx()).willReturn(txnCtx);
		given(ctx.nonBlockingHandoff()).willReturn(nonBlockingHandoff);
		given(nonBlockingHandoff.offer(expectedRso)).willReturn(true);

		// when:
		subject.addRecordToStream();

		// then:
		verify(nonBlockingHandoff).offer(expectedRso);
	}

	@Test
	void doesNothingIfNoLastCreatedRecord() {
		given(recordsHistorian.lastCreatedRecord()).willReturn(Optional.empty());
		given(ctx.recordsHistorian()).willReturn(recordsHistorian);

		// when:
		subject.addRecordToStream();

		// then:
		verifyNoInteractions(recordStreamManager);
	}
}
