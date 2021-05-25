package com.hedera.services.legacy.services.state;

import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;


@ExtendWith(MockitoExtension.class)
class RecordMgmtTest {
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

	private AwareProcessLogic subject;

	@BeforeEach
	void setUp() {
		subject = new AwareProcessLogic(ctx);
	}

	@Test
	void streamsRecordIfPresent() {
		// setup:
		final Transaction txn = Transaction.getDefaultInstance();
		final ExpirableTxnRecord lastRecord = ExpirableTxnRecord.newBuilder().build();
		final RecordStreamObject expectedRso = new RecordStreamObject(lastRecord.asGrpc(), txn, consensusNow);

		given(txnAccessor.getBackwardCompatibleSignedTxn()).willReturn(txn);
		given(txnCtx.accessor()).willReturn(txnAccessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(recordsHistorian.lastCreatedRecord()).willReturn(Optional.of(lastRecord));
		given(ctx.recordsHistorian()).willReturn(recordsHistorian);
		given(ctx.txnCtx()).willReturn(txnCtx);
		given(ctx.recordStreamManager()).willReturn(recordStreamManager);

		// when:
		subject.addRecordToStream();

		// then:
		verify(recordStreamManager).addRecordStreamObject(expectedRso);
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