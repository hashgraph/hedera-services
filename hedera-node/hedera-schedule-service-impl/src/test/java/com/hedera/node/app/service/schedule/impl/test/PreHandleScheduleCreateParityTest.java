package com.hedera.node.app.service.schedule.impl.test;

import com.hedera.node.app.service.schedule.impl.SchedulePreTransactionHandlerImpl;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.meta.SigTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.AdapterUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;

import java.time.Instant;

import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

class PreHandleScheduleCreateParityTest {
    private TransactionMetadata metadata;
    private PreHandleDispatcher dispatcher;
    private SchedulePreTransactionHandlerImpl subject;

    @BeforeEach
    void setUp() {
        final var now = Instant.now();
        final var accountStore = AdapterUtils.wellKnownAccountStoreAt(now);
        subject = new SchedulePreTransactionHandlerImpl(accountStore);
    }

    @Test
    void scheduleCreateXferWithAdmin() {
        metadata = Mockito.mock(TransactionMetadata.class);
        given(dispatcher.dispatch(any(), any())).willReturn(metadata);
        final var theTxn = txnFrom(SCHEDULE_CREATE_XFER_WITH_ADMIN);
        final var meta =
                subject.preHandleCreateSchedule(
                        theTxn,
                        theTxn.getTransactionID().getAccountID(),
                        dispatcher);
    }

    private void withMockDispatcher() {
        dispatcher = Mockito.mock(PreHandleDispatcher.class);
    }

    private TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return scenario.platformTxn().getTxn();
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}