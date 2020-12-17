package com.hedera.services.txns.schedule;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
public class ScheduleCreateTransitionLogicTest {
    private OptionValidator validator;
    private ScheduleStore store;
    private HederaLedger ledger;
    private PlatformTxnAccessor accessor;
    private TransactionContext txnCtx;

    long thisSecond = 1_234_567L;
    private Instant now = Instant.ofEpochSecond(thisSecond);

    private AccountID payer = IdUtils.asAccount("1.2.3");
    private ScheduleID resultingSchedule = IdUtils.asSchedule("2.4.6");

    private TransactionBody scheduleCreateTxn;
    final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    final private Key invalidKey = Key.newBuilder().build();

    private final boolean no = false;
    private final boolean yes = true;

    private SignatureMap sigMap;

    private ScheduleCreateTransitionLogic subject;

    private CreationResult created;

    @BeforeEach
    private void setup() {
        validator = mock(OptionValidator.class);
        store = mock(ScheduleStore.class);
        ledger = mock(HederaLedger.class);
        accessor = mock(PlatformTxnAccessor.class);

        txnCtx = mock(TransactionContext.class);
        given(txnCtx.activePayer()).willReturn(payer);

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
    public void followsHappyPath() {
        // given:
        givenValidTxnCtx();
        var transactionBodyBytes = scheduleCreateTxn.toByteArray();
        var now = RichInstant.fromJava(txnCtx.consensusTime());

        JKey jAdminKey = null;
        try {
            jAdminKey = JKey.mapKey(key);
        } catch (DecoderException e) {
            e.printStackTrace();
        }

        // and:
        given(store.createProvisionally(
                eq(this.scheduleCreateTxn.getScheduleCreation().getTransactionBody().toByteArray()),
                eq(payer),
                eq(payer),
                eq(now),
                eq(Optional.of(jAdminKey)))).willReturn(CreationResult.success(resultingSchedule));
        // when:
        subject.doStateTransition();

        // then
        verify(store).createProvisionally(
                eq(this.scheduleCreateTxn.getScheduleCreation().getTransactionBody().toByteArray()),
                eq(payer),
                eq(payer),
                eq(now),
                eq(Optional.of(jAdminKey)));
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    public void failsOnExecuteImmediatelyFalse() {
        givenCtx(
                true,
                false);

        // expect:
        assertEquals(NOT_SUPPORTED, subject.validate(scheduleCreateTxn));
    }

    @Test
    public void failsOnInvalidAdminKey() {
        givenCtx(
                false,
                true);

        // expect:
        assertEquals(INVALID_ADMIN_KEY, subject.validate(scheduleCreateTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(
                false,
                false);
    }

    private void givenCtx(
            boolean invalidExecuteImmediately,
            boolean invalidAdminKey
            ) {
        sigMap = SignatureMap.newBuilder().addSigPair(SignaturePair.newBuilder().build()).build();

        var builder = TransactionBody.newBuilder();
        var scheduleCreate = ScheduleCreateTransactionBody.newBuilder()
                .setSigMap(sigMap)
                .setAdminKey(key)
                .setExecuteImmediately(yes)
                .setPayer(payer);

        if (invalidExecuteImmediately) {
            scheduleCreate.setExecuteImmediately(no);
        }

        if (invalidAdminKey) {
            scheduleCreate.setAdminKey(invalidKey);
        }
        var c = scheduleCreate.build();
        builder.setScheduleCreation(c);

        this.scheduleCreateTxn = builder.build();
        given(accessor.getTxn()).willReturn(this.scheduleCreateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(now);
    }
}
