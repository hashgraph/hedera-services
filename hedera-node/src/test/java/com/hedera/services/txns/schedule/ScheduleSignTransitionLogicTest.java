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
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.ThresholdAccount;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
public class ScheduleSignTransitionLogicTest {
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
    private ThresholdAccount signers;

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

    @Test
    public void failsOnInvalidScheduleId() {
        givenCtx(true, false);

        // expect:
        assertEquals(INVALID_SCHEDULE_ID, subject.validate(scheduleSignTxn));
    }

    @Test
    public void failsOnInvalidSigMap() {
        givenCtx(false, true);

        // expect:
        assertEquals(EMPTY_SIGNATURES, subject.validate(scheduleSignTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(false, false);
    }

    private void givenCtx(
            boolean invalidScheduleId,
            boolean invalidSigMap
    ) {
        sigMap = SignatureMap.newBuilder().addSigPair(
                SignaturePair.newBuilder().build()
        ).build();
        var signersBuilder = ThresholdAccount.newBuilder()
                .addAccounts(signer)
                .addAccounts(anotherSigner);
        signers = signersBuilder
                .build();

        var builder = TransactionBody.newBuilder();
        var scheduleSign = ScheduleSignTransactionBody.newBuilder()
                .setSigMap(sigMap)
                .setSchedule(schedule);

        if (invalidScheduleId) {
            scheduleSign.clearSchedule();
        }

        if (invalidSigMap) {
            scheduleSign.setSigMap(SignatureMap.newBuilder().build());
        }

        builder.setScheduleSign(scheduleSign);

        scheduleSignTxn = builder.build();
        given(accessor.getTxn()).willReturn(scheduleSignTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}
