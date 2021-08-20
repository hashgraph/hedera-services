package com.hedera.services.state.logic;

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
import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
class RecordStreamingTest {
	private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);

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

	private RecordStreaming subject;

	@BeforeEach
	void setUp() {
		subject = new RecordStreaming(txnCtx, nonBlockingHandoff, runningHashUpdate, recordsHistorian);
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
		// setup:
		final Transaction txn = Transaction.getDefaultInstance();
		final ExpirableTxnRecord lastRecord = ExpirableTxnRecord.newBuilder().build();
		final RecordStreamObject expectedRso = new RecordStreamObject(lastRecord, txn, consensusNow);

		given(accessor.getSignedTxnWrapper()).willReturn(txn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(recordsHistorian.lastCreatedRecord()).willReturn(Optional.of(lastRecord));
		given(nonBlockingHandoff.offer(expectedRso))
				.willReturn(false)
				.willReturn(true);

		// when:
		subject.run();

		// then:
		verify(nonBlockingHandoff, times(2)).offer(expectedRso);
	}
}