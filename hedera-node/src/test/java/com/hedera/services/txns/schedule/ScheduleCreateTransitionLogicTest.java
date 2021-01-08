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
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
public class ScheduleCreateTransitionLogicTest {
    private OptionValidator validator;
    private ScheduleStore store;
    private HederaLedger ledger;
    private PlatformTxnAccessor accessor;
    private TransactionContext txnCtx;

    private AccountID payer = IdUtils.asAccount("1.2.3");

    private TransactionBody scheduleCreateTxn;
    final private Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    final private Key invalidKey = Key.newBuilder().build();

    private final boolean no = false;
    private final boolean yes = true;

    private SignatureMap sigMap;

    private ScheduleCreateTransitionLogic subject;

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
    public void doStateTransitionIsUnsupported() {
        givenValidTxnCtx();
        assertThrows(UnsupportedOperationException.class, () -> subject.doStateTransition());
    }

    @Test
    public void failsOnInvalidAdminKey() {
        givenCtx(
                true);

        // expect:
        assertEquals(INVALID_ADMIN_KEY, subject.validate(scheduleCreateTxn));
    }

    @Test
    public void syntaxCheckWorks() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.syntaxCheck().apply(scheduleCreateTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(
                false);
    }

    private void givenCtx(
            boolean invalidAdminKey
            ) {
        sigMap = SignatureMap.newBuilder().addSigPair(SignaturePair.newBuilder().build()).build();

        var builder = TransactionBody.newBuilder();
        var scheduleCreate = ScheduleCreateTransactionBody.newBuilder()
                .setSigMap(sigMap)
                .setAdminKey(key)
                .setPayerAccountID(payer);

        if (invalidAdminKey) {
            scheduleCreate.setAdminKey(invalidKey);
        }

        builder.setScheduleCreate(scheduleCreate);

        scheduleCreateTxn = builder.build();
        given(accessor.getTxn()).willReturn(scheduleCreateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}
