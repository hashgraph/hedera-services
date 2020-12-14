package com.hedera.services.txns.schedule;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.schedules.ScheduleStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import proto.ScheduleCreate;
import proto.ScheduleSign;

import java.time.Instant;

import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
public class ScheduleSignTransitionLogicTest {
    long thisSecond = 1_234_567L;
    private Instant now = Instant.ofEpochSecond(thisSecond);
    private OptionValidator validator;
    private ScheduleStore store;
    private HederaLedger ledger;
    private PlatformTxnAccessor accessor;
    private TransactionContext txnCtx;

    private AccountID payer = IdUtils.asAccount("1.2.3");
    private AccountID signer = IdUtils.asAccount("0.0.2");
    private AccountID anotherSigner = IdUtils.asAccount("0.0.3");

    private TransactionBody scheduleSignTxn;
    final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();

    private final boolean no = false;
    private final boolean yes = true;

    private SignatureMap sigMap;
    private ScheduleCreate.ThresholdAccounts signers;

    private ScheduleSignTransitionLogic subject;
    private ScheduleID schedule = IdUtils.asSchedule("1.2.3");

    @BeforeEach
    private void setup() {
        validator = mock(OptionValidator.class);
        store = mock(ScheduleStore.class);
        ledger = mock(HederaLedger.class);
        accessor = mock(PlatformTxnAccessor.class);

        txnCtx = mock(TransactionContext.class);
        given(txnCtx.activePayer()).willReturn(payer);
        given(txnCtx.consensusTime()).willReturn(Instant.now());

        subject = new ScheduleSignTransitionLogic(validator, store, ledger, txnCtx);
    }

    @Test
    public void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(scheduleSignTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    private void givenValidTxnCtx() {
        sigMap = SignatureMap.newBuilder().addSigPair(SignaturePair.newBuilder().build()).build();
        signers = ScheduleCreate.ThresholdAccounts.newBuilder()
                .addAccounts(signer)
                .addAccounts(anotherSigner)
                .build();

        var builder = TransactionBody.newBuilder()
                .setScheduleSign(ScheduleSign.ScheduleSignTransactionBody.newBuilder()
                        .setSigMap(sigMap)
                        .setSchedule(schedule));

        scheduleSignTxn = builder.build();
        given(accessor.getTxn()).willReturn(scheduleSignTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(now);
    }
}
