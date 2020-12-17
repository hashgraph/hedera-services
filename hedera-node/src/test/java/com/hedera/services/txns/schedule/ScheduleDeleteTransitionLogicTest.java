package com.hedera.services.txns.schedule;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
public class ScheduleDeleteTransitionLogicTest {
    private OptionValidator validator;
    private ScheduleStore store;
    private HederaLedger ledger;
    private PlatformTxnAccessor accessor;
    private TransactionContext txnCtx;

    private ScheduleID schedule = IdUtils.asSchedule("1.2.3");

    private TransactionBody scheduleDeleteTxn;
    private ScheduleDeleteTransitionLogic subject;

    @BeforeEach
    private void setup() {
        validator = mock(OptionValidator.class);
        store = mock(ScheduleStore.class);
        ledger = mock(HederaLedger.class);
        accessor = mock(PlatformTxnAccessor.class);
        txnCtx = mock(TransactionContext.class);
        subject = new ScheduleDeleteTransitionLogic(validator, store, ledger, txnCtx);
    }

    @Test
    public void followsHappyPath() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.delete(schedule)).willReturn(OK);

        // when:
        subject.doStateTransition();

        // then
        verify(store).delete(schedule);
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    public void capturesInvalidScheduleId() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.delete(schedule)).willReturn(INVALID_SCHEDULE_ID);

        // when:
        subject.doStateTransition();

        // then
        verify(store).delete(schedule);
        verify(txnCtx).setStatus(INVALID_SCHEDULE_ID);
    }

    @Test
    public void capturesImmutableSchedule() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.delete(schedule)).willReturn(SCHEDULE_IS_IMMUTABLE);

        // when:
        subject.doStateTransition();

        // then
        verify(store).delete(schedule);
        verify(txnCtx).setStatus(SCHEDULE_IS_IMMUTABLE);
    }

    @Test
    public void capturesAlreadyDeletedSchedule() {
        // given:
        givenValidTxnCtx();

        // and:
        given(store.delete(schedule)).willReturn(SCHEDULE_WAS_DELETED);

        // when:
        subject.doStateTransition();

        // then
        verify(store).delete(schedule);
        verify(txnCtx).setStatus(SCHEDULE_WAS_DELETED);
    }

    @Test
    public void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(scheduleDeleteTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    public void failsOnInvalidSchedule() {
        givenCtx(true);

        // expect:
        assertEquals(INVALID_SCHEDULE_ID, subject.validate(scheduleDeleteTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(false);
    }


    private void givenCtx(
            boolean invalidScheduleId
    ) {
        var builder = TransactionBody.newBuilder();
        var scheduleDelete = ScheduleDeleteTransactionBody.newBuilder()
                .setSchedule(schedule);

        if (invalidScheduleId) {
            scheduleDelete.clearSchedule();
        }

        builder.setScheduleDelete(scheduleDelete);

        scheduleDeleteTxn = builder.build();
        given(accessor.getTxn()).willReturn(scheduleDeleteTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}
