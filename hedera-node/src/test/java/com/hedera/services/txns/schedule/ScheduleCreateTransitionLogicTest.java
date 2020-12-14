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
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import proto.ScheduleCreate;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_SIGNERS_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
public class ScheduleCreateTransitionLogicTest {
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

    private TransactionBody scheduleCreateTxn;
    final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    final private Key invalidKey = Key.newBuilder().build();

    private final boolean no = false;
    private final boolean yes = true;

    private SignatureMap sigMap;
    private ScheduleCreate.ThresholdAccounts signers;

    private ScheduleCreateTransitionLogic subject;

    @BeforeEach
    private void setup() {
        validator = mock(OptionValidator.class);
        store = mock(ScheduleStore.class);
        ledger = mock(HederaLedger.class);
        accessor = mock(PlatformTxnAccessor.class);

        txnCtx = mock(TransactionContext.class);
        given(txnCtx.activePayer()).willReturn(payer);
        given(txnCtx.consensusTime()).willReturn(Instant.now());

        subject = new ScheduleCreateTransitionLogic(validator, store, ledger, txnCtx);
    }

    @Test
    public void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(scheduleCreateTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    public void failsOnExecuteImmediatelyFalse() {
        givenCtx(
                true,
                false,
                false,
                false);

        // expect:
        assertEquals(NOT_SUPPORTED, subject.validate(scheduleCreateTxn));
    }

    @Test
    public void failsOnInvalidAdminKey() {
        givenCtx(
                false,
                true,
                false,
                false);

        // expect:
        assertEquals(INVALID_ADMIN_KEY, subject.validate(scheduleCreateTxn));
    }

    @Test
    public void failsOnInvalidSigners() {
        givenCtx(
                false,
                false,
                true,
                false);

        // expect:
        assertEquals(EMPTY_SIGNERS_LIST, subject.validate(scheduleCreateTxn));
    }

    @Test
    public void failsOnTooLargeThreshold() {
        givenCtx(
                false,
                false,
                false,
                true);

        // expect:
        assertEquals(INVALID_SCHEDULE_THRESHOLD, subject.validate(scheduleCreateTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(
                false,
                false,
                false,
                false);
    }

    private void givenCtx(
            boolean invalidExecuteImmediately,
            boolean invalidAdminKey,
            boolean invalidSigners,
            boolean invalidThreshold
            ) {
        sigMap = SignatureMap.newBuilder().addSigPair(SignaturePair.newBuilder().build()).build();
        var signersBuilder = ScheduleCreate.ThresholdAccounts.newBuilder()
                .addAccounts(signer)
                .addAccounts(anotherSigner);
        signers = signersBuilder
                .build();

        var builder = TransactionBody.newBuilder();
        var scheduleCreate = ScheduleCreate.ScheduleCreateTransactionBody.newBuilder()
                .setSigMap(sigMap)
                .setSigners(signers)
                .setAdminKey(key)
                .setExecuteImmediately(yes);

        if (invalidExecuteImmediately) {
            scheduleCreate.setExecuteImmediately(no);
        }

        if (invalidAdminKey) {
            scheduleCreate.setAdminKey(invalidKey);
        }

        if (invalidSigners) {
            scheduleCreate.setSigners(ScheduleCreate.ThresholdAccounts.newBuilder());
        }

        if (invalidThreshold) {
            signersBuilder.setThreshold(123123123);
            scheduleCreate.setSigners(signersBuilder.build());
        }

        builder.setScheduleCreation(scheduleCreate);

        scheduleCreateTxn = builder.build();
        given(accessor.getTxn()).willReturn(scheduleCreateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(now);
    }
}
